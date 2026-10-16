import { describe, expect, it } from "vitest";
import { SaynataApi } from "../src/shared/api";
import { explain } from "../src/shared/explain";
import type { ExplainEvent } from "../src/shared/messages";
import { event, fakeFetch, json, sentenceResponse, sse, wordResponse } from "./helpers";

async function run(text: string, ...handlers: Parameters<typeof fakeFetch>) {
  const { fetchImpl, calls } = fakeFetch(...handlers);
  const events: ExplainEvent[] = [];
  await explain(new SaynataApi("https://api.test", undefined, fetchImpl), { text, targetLang: "vi" }, (e) => events.push(e));
  return { events, calls };
}

describe("explain", () => {
  it("one word goes to the word API", async () => {
    const { events, calls } = await run("Reluctant.", () => json(wordResponse));
    expect(calls[0].url).toBe("https://api.test/api/words/reluctant");
    expect(events).toEqual([{ type: "word", response: wordResponse }]);
  });

  it("more than one word goes to the stream, with the meaning before the result", async () => {
    const { events } = await run(
      "reluctant to approve the plan",
      () => sse(event("meaning-delta", { text: "Not" }), event("meaning", { meaning: "Not wanting." }), event("done", sentenceResponse)),
    );
    expect(events.map((e) => e.type)).toEqual(["meaningDelta", "meaning", "sentence"]);
  });

  it("errors become one error event", async () => {
    const { events } = await run("zyzzyva", () => json({ status: 401, code: "sign_in_required" }, 401));
    expect(events).toEqual([{ type: "error", kind: "signInRequired", message: "Sign in to get a new explanation." }]);
  });

  it("refuses empty and long text without a request", async () => {
    expect((await run("   ")).events).toEqual([{ type: "error", kind: "empty", message: "Select some English text first." }]);
    const long = await run("word ".repeat(120));
    expect(long.events[0]).toMatchObject({ kind: "tooLong", message: "Select up to 500 characters." });
    expect(long.calls).toHaveLength(0);
  });
});
