/**
 * Decides whether selected text is one word (word API) or a sentence (sentence API), like the iOS app
 * (CacheKeys.wordQuery): one token, or "a/an/the" plus one token, is a word.
 */

export const MAX_SENTENCE_LENGTH = 500;

/** GET /api/words/{word} accepts only letters, ' and -, 1-64 characters (WordController). */
const WORD = /^[\p{L}'-]{1,64}$/u;
const ARTICLES = new Set(["a", "an", "the"]);

export type TextKind =
  | { kind: "word"; word: string }
  | { kind: "sentence"; text: string }
  | { kind: "empty" }
  | { kind: "tooLong"; length: number };

/** Collapses spaces and line breaks, and turns curly quotes into straight ones. */
export function cleanSelection(raw: string): string {
  return raw
    .replace(/[‘’]/g, "'")
    .replace(/[“”]/g, '"')
    .replace(/\s+/g, " ")
    .trim();
}

export function classify(raw: string): TextKind {
  const text = cleanSelection(raw);
  if (text.length === 0) return { kind: "empty" };
  if (text.length > MAX_SENTENCE_LENGTH) return { kind: "tooLong", length: text.length };
  const word = wordQuery(text);
  return word ? { kind: "word", word } : { kind: "sentence", text };
}

/** The word to look up, or null when the text is not a single word. Strips edge punctuation ("apple." is apple). */
export function wordQuery(text: string): string | null {
  const tokens = text.split(" ").map(trimPunctuation).filter((token) => token.length > 0);
  const word =
    tokens.length === 1 ? tokens[0] : tokens.length === 2 && ARTICLES.has(tokens[0].toLowerCase()) ? tokens[1] : null;
  if (!word || !WORD.test(word)) return null;
  return word.toLowerCase();
}

function trimPunctuation(token: string): string {
  return token.replace(/^[\p{P}\p{S}]+|[\p{P}\p{S}]+$/gu, "");
}
