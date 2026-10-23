/**
 * One search from start to end, kept out of React so it can be tested: `startState` is what the page shows at
 * once, `fetchResult` fetches and reports every later state. A word goes to GET /api/words/{word}, more to the
 * sentence stream (doc-4, doc-5). A guest gets cached and bundled answers only (doc-17).
 */
import { ApiError, MESSAGES, type Explanation, type SaynataApi, type WordResponse } from "./api";
import type { LanguageCode } from "./languages";
import { classify, MAX_SENTENCE_LENGTH } from "./textKind";

export type Result =
  | { kind: "idle" }
  | { kind: "loading"; query: string }
  | { kind: "word"; query: string; word: WordResponse }
  | { kind: "sentence"; query: string; meaning: string | null; explanation: Explanation | null }
  | { kind: "error"; query: string; message: string };

export const SIGN_IN_MESSAGE =
  "Sign in to explain new text. Dictionary words and common sentences work without an account.";

export function startState(raw: string): Result {
  const text = classify(raw);
  switch (text.kind) {
    case "empty":
      return { kind: "idle" };
    case "tooLong":
      return { kind: "error", query: raw, message: `Make it shorter: up to ${MAX_SENTENCE_LENGTH} characters.` };
    case "word":
      return { kind: "loading", query: raw };
    case "sentence":
      return { kind: "sentence", query: text.text, meaning: null, explanation: null };
  }
}

/** Fetches the answer for `raw` and calls `update` with each new state. Nothing is reported after `signal` aborts. */
export async function fetchResult(
  api: Pick<SaynataApi, "lookUpWord" | "explainSentence">,
  raw: string,
  targetLang: LanguageCode,
  update: (next: (current: Result) => Result) => void,
  signal: AbortSignal,
): Promise<void> {
  const text = classify(raw);
  const report = (next: (current: Result) => Result) => {
    if (!signal.aborted) update(next);
  };
  try {
    if (text.kind === "word") {
      const word = await api.lookUpWord(text.word, signal);
      report(() => ({ kind: "word", query: raw, word }));
    } else if (text.kind === "sentence") {
      let meaning = "";
      const sentence = (current: Result, change: Partial<Extract<Result, { kind: "sentence" }>>): Result =>
        current.kind === "sentence" ? { ...current, ...change } : current;
      const response = await api.explainSentence(
        text.text,
        targetLang,
        {
          onMeaningDelta: (delta) => {
            meaning += delta;
            const so = meaning;
            report((current) => sentence(current, { meaning: so }));
          },
          onMeaning: (full) => report((current) => sentence(current, { meaning: full })),
        },
        signal,
      );
      report(() => ({
        kind: "sentence",
        query: text.text,
        meaning: response.explanation.meaning,
        explanation: response.explanation,
      }));
    }
  } catch (error) {
    report(() => ({ kind: "error", query: raw, message: errorMessage(error) }));
  }
}

export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return MESSAGES.generic;
  if (error.kind === "signInRequired") return SIGN_IN_MESSAGE;
  if (error.status === 404) return "This word is not in the dictionary yet. Check the spelling.";
  return error.message;
}
