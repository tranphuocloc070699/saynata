import { describe, expect, it } from "vitest";
import { ApiError, errorFrom, rateLimitMessage, SaynataApi, type TokenSource } from "../src/lib/api";
import { event, fakeFetch, header, json, sentenceResponse, sse, wordResponse } from "./helpers";

const BASE = "https://api.test";

/** Tokens with a fixed access token and a refresh that gives `fresh` (or null = signed out). */
function tokens(access: string | null, fresh: string | null = "new-token") {
  const refreshed: string[] = [];
  const source: TokenSource = {
    accessToken: async () => access,
    refreshedAccessToken: async (old) => {
      refreshed.push(old);
      return fresh;
    },
  };
  return { source, refreshed };
}

describe("lookUpWord", () => {
  it("calls GET /api/words/{word} with no Authorization for a guest", async () => {
    const { fetchImpl, calls } = fakeFetch(() => json(wordResponse));
    const response = await new SaynataApi(BASE, undefined, fetchImpl).lookUpWord("don't");
    expect(response.entry.senses[0].simpleDefinition).toBe("Not wanting to do something.");
    expect(calls[0].url).toBe(`${BASE}/api/words/don't`);
    expect(header(calls[0], "Authorization")).toBeUndefined();
  });

  it("sends the Bearer token when signed in", async () => {
    const { fetchImpl, calls } = fakeFetch(() => json(wordResponse));
    await new SaynataApi(BASE, tokens("abc").source, fetchImpl).lookUpWord("run");
    expect(header(calls[0], "Authorization")).toBe("Bearer abc");
  });

  it("refreshes once on a 401 with a token and sends the request again", async () => {
    const { source, refreshed } = tokens("old");
    const { fetchImpl, calls } = fakeFetch(
      () => json({ status: 401 }, 401),
      () => json(wordResponse),
    );
    await new SaynataApi(BASE, source, fetchImpl).lookUpWord("run");
    expect(refreshed).toEqual(["old"]);
    expect(header(calls[1], "Authorization")).toBe("Bearer new-token");
  });

  it("retries only once: a second 401 is an error", async () => {
    const { fetchImpl, calls } = fakeFetch(
      () => json({ status: 401 }, 401),
      () => json({ status: 401, code: "sign_in_required" }, 401),
    );
    const error = await new SaynataApi(BASE, tokens("old").source, fetchImpl).lookUpWord("run").catch((e) => e);
    expect(error).toMatchObject({ kind: "signInRequired" });
    expect(calls).toHaveLength(2);
  });

  it("asks to sign in when the refresh fails", async () => {
    const { fetchImpl, calls } = fakeFetch(() => json({ status: 401 }, 401));
    const error = await new SaynataApi(BASE, tokens("old", null).source, fetchImpl).lookUpWord("run").catch((e) => e);
    expect(error).toMatchObject({ kind: "signInRequired", message: "Sign in to get a new explanation." });
    expect(calls).toHaveLength(1);
  });

  it("gives a guest's 401 straight to the caller, with no refresh", async () => {
    const { source, refreshed } = tokens(null);
    const { fetchImpl } = fakeFetch(() => json({ status: 401, code: "sign_in_required" }, 401));
    const error = await new SaynataApi(BASE, source, fetchImpl).lookUpWord("zyzzyva").catch((e) => e);
    expect(error).toMatchObject({ kind: "signInRequired" });
    expect(refreshed).toEqual([]);
  });

  it("turns a network failure into a clear message", async () => {
    const { fetchImpl } = fakeFetch(() => {
      throw new TypeError("Failed to fetch");
    });
    const error = await new SaynataApi(BASE, undefined, fetchImpl).lookUpWord("run").catch((e) => e);
    expect(error).toMatchObject({ kind: "network" });
  });
});

describe("explainSentence (stream)", () => {
  it("posts to the stream and reports deltas, the meaning, then the result", async () => {
    const { fetchImpl, calls } = fakeFetch(() =>
      sse(
        event("meaning-delta", { text: "Not " }),
        event("meaning-delta", { text: "wanting" }).slice(0, 10),
        event("meaning-delta", { text: "wanting" }).slice(10) + event("meaning", { meaning: "Not wanting." }),
        event("done", sentenceResponse),
      ),
    );
    const seen: string[] = [];
    const response = await new SaynataApi(BASE, undefined, fetchImpl).explainSentence("reluctant to approve", "vi", {
      onMeaningDelta: (text) => seen.push(`delta:${text}`),
      onMeaning: (meaning) => seen.push(`meaning:${meaning}`),
    });
    expect(seen).toEqual(["delta:Not ", "delta:wanting", "meaning:Not wanting."]);
    expect(response.explanation.translation).toBe("miễn cưỡng chấp thuận kế hoạch");
    expect(calls[0].url).toBe(`${BASE}/api/translations/stream`);
    expect(JSON.parse(String(calls[0].init.body))).toEqual({ text: "reluctant to approve", targetLang: "vi" });
    expect(header(calls[0], "Accept")).toBe("text/event-stream");
  });

  it("a cache hit sends only done", async () => {
    const { fetchImpl } = fakeFetch(() => sse(event("done", { ...sentenceResponse, cached: true })));
    const response = await new SaynataApi(BASE, undefined, fetchImpl).explainSentence("hi there", "vi");
    expect(response.cached).toBe(true);
  });

  it("maps a coded error event and does not fall back for a 4xx", async () => {
    const { fetchImpl, calls } = fakeFetch(() =>
      sse(event("error", { status: 401, message: "Sign in", code: "sign_in_required" })),
    );
    const error = await new SaynataApi(BASE, undefined, fetchImpl).explainSentence("new sentence", "vi").catch((e) => e);
    expect(error).toMatchObject({ kind: "signInRequired" });
    expect(calls).toHaveLength(1);
  });

  it("a not-English 422 is final", async () => {
    const { fetchImpl, calls } = fakeFetch(() => sse(event("error", { status: 422, message: "This is not English." })));
    const error = await new SaynataApi(BASE, undefined, fetchImpl).explainSentence("xin chào bạn", "vi").catch((e) => e);
    expect(error).toMatchObject({ kind: "notEnglish", message: "This is not English." });
    expect(calls).toHaveLength(1);
  });

  it("refreshes on a 401 error event with a token, then streams again", async () => {
    const { source, refreshed } = tokens("old");
    const { fetchImpl, calls } = fakeFetch(
      () => sse(event("error", { status: 401, message: "expired" })),
      () => sse(event("done", sentenceResponse)),
    );
    await new SaynataApi(BASE, source, fetchImpl).explainSentence("new sentence", "vi");
    expect(refreshed).toEqual(["old"]);
    expect(header(calls[1], "Authorization")).toBe("Bearer new-token");
  });

  it("falls back to POST /api/translations once when the stream ends early", async () => {
    const { fetchImpl, calls } = fakeFetch(
      () => sse(event("meaning", { meaning: "Half" })),
      () => json(sentenceResponse),
    );
    const response = await new SaynataApi(BASE, undefined, fetchImpl).explainSentence("new sentence", "vi");
    expect(response.explanation.meaning).toBe("Not wanting to say yes to the plan.");
    expect(calls[1].url).toBe(`${BASE}/api/translations`);
  });

  it("falls back on a 5xx error event, and reports the fallback's error", async () => {
    const { fetchImpl, calls } = fakeFetch(
      () => sse(event("error", { status: 502, message: "Claude failed" })),
      () => json({ status: 503, code: "daily_limit_reached", detail: "Saynata has reached today's limit." }, 503),
    );
    const error = await new SaynataApi(BASE, undefined, fetchImpl).explainSentence("new sentence", "vi").catch((e) => e);
    expect(error).toMatchObject({ kind: "dailyLimit", message: "Saynata has reached today's limit." });
    expect(calls).toHaveLength(2);
  });

  it("does not fall back after a cancel", async () => {
    const controller = new AbortController();
    const { fetchImpl, calls } = fakeFetch(() => {
      controller.abort();
      throw new DOMException("aborted", "AbortError");
    });
    await expect(
      new SaynataApi(BASE, undefined, fetchImpl).explainSentence("new sentence", "vi", {}, controller.signal),
    ).rejects.toThrow();
    expect(calls).toHaveLength(1);
  });
});

describe("errors", () => {
  it("reads the code first", () => {
    expect(errorFrom(429, { code: "rate_limited" }, "125")).toMatchObject({
      kind: "rateLimited",
      message: "Too many requests. Try again in 3 minutes.",
    });
    expect(errorFrom(503, { code: "daily_limit_reached", detail: "Limit." })).toMatchObject({
      kind: "dailyLimit",
      message: "Limit.",
    });
  });

  it("falls back to the status", () => {
    expect(errorFrom(401, null).kind).toBe("signInRequired");
    expect(errorFrom(400, { detail: "Text must be 1-500 characters." }).message).toBe("Text must be 1-500 characters.");
    expect(errorFrom(500, null)).toMatchObject({ kind: "server", message: "Something went wrong. Try again." });
  });

  it("writes the rate-limit wait in whole minutes", () => {
    expect(rateLimitMessage("30")).toBe("Too many requests. Try again in 1 minute.");
    expect(rateLimitMessage(null)).toBe("Too many requests. Try again in a little while.");
  });

  it("knows which errors are final", () => {
    expect(new ApiError("badRequest", "", 400).isFinalAnswer).toBe(true);
    expect(new ApiError("server", "", 502).isFinalAnswer).toBe(false);
    expect(new ApiError("network", "").isFinalAnswer).toBe(false);
  });
});

describe("suggest", () => {
  it("calls GET /api/suggest with the prefix and language and returns the list", async () => {
    const { fetchImpl, calls } = fakeFetch(() =>
      json({ query: "giv", suggestions: [{ text: "give up", kind: "word" }] }),
    );
    const suggestions = await new SaynataApi(BASE, undefined, fetchImpl).suggest("giv", "vi");
    expect(calls[0].url).toBe(`${BASE}/api/suggest?q=giv&lang=vi`);
    expect(suggestions).toEqual([{ text: "give up", kind: "word" }]);
  });

  it("turns a server error into an ApiError", async () => {
    const { fetchImpl } = fakeFetch(() => json({ detail: "boom" }, 500));
    const error = await new SaynataApi(BASE, undefined, fetchImpl).suggest("g", "vi").catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
  });
});
