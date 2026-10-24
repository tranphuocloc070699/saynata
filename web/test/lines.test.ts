import { describe, expect, it } from "vitest";
import { ApiError, SaynataApi, type TokenSource } from "../src/lib/api";
import { LineCache, type LineState, lineErrorMessage, lineTexts, loadLines, rowContent } from "../src/lib/lines";
import { fakeFetch, header, json } from "./helpers";

const BASE = "https://api.test";
const signedIn: TokenSource = { accessToken: async () => "abc", refreshedAccessToken: async () => null };

const definitions = ["Not wanting to do something.", "A place that keeps money safe."];
const dictionary = ["miễn cưỡng", "ngân hàng"];

describe("translateLines", () => {
  it("signed in: normal call with the Bearer token and all texts in one request", async () => {
    const { fetchImpl, calls } = fakeFetch(() =>
      json({ targetLang: "ko", lines: [{ text: "a", translation: "하나", cached: false }, { text: "b", translation: "둘", cached: true }] }),
    );
    const result = await new SaynataApi(BASE, signedIn, fetchImpl).translateLines(["a", "b"], "ko");
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe(`${BASE}/api/translate-lines`);
    expect(header(calls[0], "Authorization")).toBe("Bearer abc");
    expect(JSON.parse(calls[0].init.body as string)).toEqual({ targetLang: "ko", texts: ["a", "b"] });
    expect(result).toEqual({ translations: ["하나", "둘"], signedIn: true });
  });

  it("guest: cachedOnly=true and no Authorization; a miss is null", async () => {
    const { fetchImpl, calls } = fakeFetch(() =>
      json({ targetLang: "ko", lines: [{ text: "a", translation: null, cached: false }] }),
    );
    const result = await new SaynataApi(BASE, undefined, fetchImpl).translateLines(["a"], "ko");
    expect(calls[0].url).toBe(`${BASE}/api/translate-lines?cachedOnly=true`);
    expect(header(calls[0], "Authorization")).toBeUndefined();
    expect(result).toEqual({ translations: [null], signedIn: false });
  });

  it("an error response throws an ApiError", async () => {
    const { fetchImpl } = fakeFetch(() => json({ code: "rate_limited" }, 429, { "Retry-After": "60" }));
    await expect(new SaynataApi(BASE, signedIn, fetchImpl).translateLines(["a"], "ko")).rejects.toMatchObject({
      kind: "rateLimited",
    });
  });
});

describe("loadLines", () => {
  it("keeps lines per (text, language): the second call makes no request", async () => {
    const { fetchImpl, calls } = fakeFetch(() => json({ lines: [{ translation: "하나" }, { translation: "둘" }] }));
    const api = new SaynataApi(BASE, signedIn, fetchImpl);
    const cache = new LineCache();
    await loadLines(api, cache, "ko", ["a", "b"]);
    const again = await loadLines(api, cache, "ko", ["a", "b"]);
    expect(calls).toHaveLength(1);
    expect(again.translations).toEqual(["하나", "둘"]);
  });

  it("another language asks again; only the missing texts are sent", async () => {
    const { fetchImpl, calls } = fakeFetch(
      () => json({ lines: [{ translation: "하나" }] }),
      () => json({ lines: [{ translation: "ichi" }, { translation: "ni" }] }),
      () => json({ lines: [{ translation: "ni" }] }),
    );
    const api = new SaynataApi(BASE, signedIn, fetchImpl);
    const cache = new LineCache();
    await loadLines(api, cache, "ko", ["a"]);
    await loadLines(api, cache, "ja", ["a", "b"]);
    await loadLines(api, cache, "ko", ["a", "b"]);
    expect(JSON.parse(calls[2].init.body as string).texts).toEqual(["b"]);
  });

  it("does not remember a guest miss", async () => {
    const { fetchImpl, calls } = fakeFetch(
      () => json({ lines: [{ translation: null }] }),
      () => json({ lines: [{ translation: "하나" }] }),
    );
    const api = new SaynataApi(BASE, undefined, fetchImpl);
    const cache = new LineCache();
    expect((await loadLines(api, cache, "ko", ["a"])).translations).toEqual([null]);
    expect((await loadLines(api, cache, "ko", ["a"])).translations).toEqual(["하나"]);
    expect(calls).toHaveLength(2);
  });
});

describe("lineTexts", () => {
  it("trims, drops empty and repeated texts, and stops at 20", () => {
    expect(lineTexts([" a ", "", "a", "b"])).toEqual(["a", "b"]);
    expect(lineTexts(Array.from({ length: 30 }, (_, i) => `t${i}`))).toHaveLength(20);
  });
});

describe("rowContent", () => {
  const texts = lineTexts(definitions);

  it("signed in: the lines from the backend", () => {
    const row = rowContent("ko", definitions, dictionary, { status: "done", translations: ["하나", "둘"], signedIn: true });
    expect(row).toEqual({ kind: "lines", lines: ["하나", "둘"] });
    expect(texts).toHaveLength(2);
  });

  it("loading: Vietnamese shows the dictionary at once, other languages a loading state", () => {
    expect(rowContent("vi", definitions, dictionary, { status: "loading" })).toEqual({ kind: "lines", lines: dictionary });
    expect(rowContent("ko", definitions, dictionary, { status: "loading" })).toEqual({ kind: "loading" });
  });

  it("guest miss: Vietnamese shows the dictionary text, other languages the sign-in line", () => {
    const miss: LineState = { status: "done", translations: [null, null], signedIn: false };
    expect(rowContent("vi", definitions, dictionary, miss)).toEqual({ kind: "lines", lines: dictionary });
    expect(rowContent("ko", definitions, dictionary, miss)).toEqual({ kind: "signIn" });
  });

  it("a line from the backend wins over the dictionary", () => {
    const row = rowContent("vi", definitions, dictionary, { status: "done", translations: ["lười", null], signedIn: false });
    expect(row).toEqual({ kind: "lines", lines: ["lười", "ngân hàng"] });
  });

  it("error: Vietnamese falls back to the dictionary, other languages a short error line", () => {
    const error: LineState = { status: "error", message: "Can't reach Saynata." };
    expect(rowContent("vi", definitions, dictionary, error)).toEqual({ kind: "lines", lines: dictionary });
    expect(rowContent("ko", definitions, dictionary, error)).toEqual({ kind: "error", message: "Can't reach Saynata." });
  });

  it("error text comes from the ApiError", () => {
    expect(lineErrorMessage(new ApiError("network", "Offline."))).toBe("Offline.");
    expect(lineErrorMessage(new Error("x"))).toBe("Something went wrong. Try again.");
  });
});
