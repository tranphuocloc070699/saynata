import { describe, expect, it } from "vitest";
import { ApiError, type TranslationResponse, type WordResponse } from "../src/lib/api";
import { fetchResult, SIGN_IN_MESSAGE, startState, type Result } from "../src/lib/explain";
import { sentenceResponse as sentenceJson, wordResponse as wordJson } from "./helpers";

const wordResponse = () => structuredClone(wordJson) as WordResponse;
const sentenceResponse = () => structuredClone(sentenceJson) as TranslationResponse;

/** Runs fetchResult against a fake api and returns every state it reported, applied in order. */
async function states(api: Parameters<typeof fetchResult>[0], raw: string, controller = new AbortController()) {
  let current: Result = startState(raw);
  const seen: Result[] = [];
  await fetchResult(api, raw, "vi", (next) => seen.push((current = next(current))), controller.signal);
  return seen;
}

const never = () => Promise.reject(new Error("not called"));

describe("startState", () => {
  it("is idle for empty text, loading for a word, an empty sentence card for a sentence", () => {
    expect(startState("  ")).toEqual({ kind: "idle" });
    expect(startState("Run.")).toEqual({ kind: "loading", query: "Run." });
    expect(startState("hold  the door")).toEqual({ kind: "sentence", query: "hold the door", meaning: null, explanation: null });
  });

  it("asks for shorter text over 500 characters", () => {
    expect(startState("a ".repeat(300)).kind).toBe("error");
  });
});

describe("fetchResult", () => {
  it("looks up one word and shows the word card", async () => {
    const word = wordResponse();
    const seen = await states({ lookUpWord: async () => word, explainSentence: never }, "Run.");
    expect(seen).toEqual([{ kind: "word", query: "Run.", word }]);
  });

  it("grows the meaning with each piece, then shows the full answer", async () => {
    const response = sentenceResponse();
    const seen = await states(
      {
        lookUpWord: never,
        explainSentence: async (_text, _lang, handlers) => {
          handlers?.onMeaningDelta?.("Please ");
          handlers?.onMeaningDelta?.("wait.");
          return response;
        },
      },
      "Hold on a second",
    );
    expect(seen.map((s) => (s.kind === "sentence" ? s.meaning : null))).toEqual([
      "Please ",
      "Please wait.",
      response.explanation.meaning,
    ]);
    expect(seen.at(-1)).toMatchObject({ kind: "sentence", explanation: response.explanation });
  });

  it("explains sign-in for a guest's miss", async () => {
    const seen = await states(
      { lookUpWord: never, explainSentence: () => Promise.reject(new ApiError("signInRequired", "x", 401)) },
      "A brand new sentence",
    );
    expect(seen).toEqual([{ kind: "error", query: "A brand new sentence", message: SIGN_IN_MESSAGE }]);
  });

  it("reports nothing after a cancel", async () => {
    const controller = new AbortController();
    const seen = await states(
      {
        lookUpWord: async () => {
          controller.abort();
          return wordResponse();
        },
        explainSentence: never,
      },
      "run",
      controller,
    );
    expect(seen).toEqual([]);
  });
});
