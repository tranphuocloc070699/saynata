/**
 * The language row of a word card (TASK-106.4, doc-5): one POST /api/translate-lines for all sense explanations,
 * kept in memory per (text, language). Copied to extension/src/shared/lines.ts: a fix in one belongs in the other.
 */
import { ApiError, MESSAGES, type SaynataApi } from "./api";
import type { LanguageCode } from "./languages";

/** The backend takes 1-20 texts of 1-500 characters. */
export const MAX_LINES = 20;
export const MAX_LINE_LENGTH = 500;

/** What `SaynataApi.translateLines` gives back: one translation (or null) per text, same order. */
export interface LinesResult {
  translations: (string | null)[];
  /** False for a guest (cachedOnly), so a null means "sign in", not "failed". */
  signedIn: boolean;
}

export type LineState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "done"; translations: (string | null)[]; signedIn: boolean }
  | { status: "error"; message: string };

/** Only the languages with a row: every language except English. */
export function hasLanguageRow(lang: LanguageCode): boolean {
  return lang !== "en";
}

/** The texts to send for a card: its sense explanations, trimmed, without repeats, within the limits. */
export function lineTexts(definitions: string[]): string[] {
  const texts = definitions.map(clip).filter((text) => text.length > 0);
  return Array.from(new Set(texts)).slice(0, MAX_LINES);
}

function clip(text: string): string {
  return text.trim().slice(0, MAX_LINE_LENGTH);
}

/** Lines kept per (text, language), so opening the row again (or another card with the same text) costs nothing. */
export class LineCache {
  private readonly lines = new Map<string, string>();

  get(lang: LanguageCode, text: string): string | undefined {
    return this.lines.get(key(lang, text));
  }

  set(lang: LanguageCode, text: string, translation: string): void {
    this.lines.set(key(lang, text), translation);
  }
}

function key(lang: LanguageCode, text: string): string {
  return `${lang}\u0000${text}`;
}

/**
 * Translations for `texts`: from the cache first, then one request for the rest. A guest's misses stay null
 * and are not remembered, so after a sign-in the row asks again.
 */
export async function loadLines(
  api: Pick<SaynataApi, "translateLines">,
  cache: LineCache,
  lang: LanguageCode,
  texts: string[],
  signal?: AbortSignal,
): Promise<LinesResult> {
  const missing = texts.filter((text) => cache.get(lang, text) === undefined);
  let signedIn = true;
  if (missing.length > 0) {
    const result = await api.translateLines(missing, lang, signal);
    signedIn = result.signedIn;
    missing.forEach((text, index) => {
      const line = result.translations[index];
      if (line) cache.set(lang, text, line);
    });
  }
  return { translations: texts.map((text) => cache.get(lang, text) ?? null), signedIn };
}

/** The message for a failed line request (a short line in the row). */
export function lineErrorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : MESSAGES.generic;
}

/** What the row shows. */
export type RowContent =
  | { kind: "loading" }
  /** One line per sense, same order; null = nothing to show for that sense. */
  | { kind: "lines"; lines: (string | null)[] }
  | { kind: "signIn" }
  | { kind: "error"; message: string };

/**
 * Decides the row for a card. `definitions` are the sense explanations, `dictionary` the dictionary Vietnamese
 * of each sense (same order). `done.translations` follow `lineTexts(definitions)`. Vietnamese always has
 * something to show: the dictionary text while it loads, after a guest miss and after an error.
 */
export function rowContent(
  lang: LanguageCode,
  definitions: string[],
  dictionary: string[],
  state: LineState,
): RowContent {
  const fallback = definitions.map((_, index) => (lang === "vi" ? (dictionary[index] ?? "").trim() : ""));
  const fromDictionary = (): RowContent | null => {
    const lines = fallback.map((line) => line || null);
    return lines.some((line) => line) ? { kind: "lines", lines } : null;
  };
  switch (state.status) {
    case "idle":
    case "loading":
      return fromDictionary() ?? { kind: "loading" };
    case "error":
      return fromDictionary() ?? { kind: "error", message: state.message };
    case "done": {
      const texts = lineTexts(definitions);
      const byText = new Map(texts.map((text, index) => [text, state.translations[index]]));
      const lines = definitions.map((definition, index) => byText.get(clip(definition)) || fallback[index] || null);
      if (lines.some((line) => line)) return { kind: "lines", lines };
      return state.signedIn ? { kind: "error", message: MESSAGES.generic } : { kind: "signIn" };
    }
  }
}
