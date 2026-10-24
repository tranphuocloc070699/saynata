/** Messages between the content script, the service worker and the popup. */
import type { ApiErrorKind, TranslationResponse, WordResponse } from "./api";
import type { LanguageCode } from "./languages";

/** The content script opens a port with this name for one explanation; closing it cancels the request. */
export const EXPLAIN_PORT = "saynata-explain";

export interface ExplainRequest {
  text: string;
  targetLang: LanguageCode;
}

/** What the service worker sends back on the port, in order: deltas, meaning, then one result or an error. */
export type ExplainEvent =
  | { type: "meaningDelta"; text: string }
  | { type: "meaning"; meaning: string }
  | { type: "word"; response: WordResponse }
  | { type: "sentence"; response: TranslationResponse }
  | { type: "error"; kind: ApiErrorKind | "tooLong" | "empty"; message: string };

/** Service worker to content script (chrome.tabs.sendMessage): explain text from the context menu or the shortcut. */
export type TabMessage = { type: "explainText"; text: string } | { type: "explainSelection" };

/** Popup or content script to the service worker (chrome.runtime.sendMessage). Only the worker changes the session. */
export type RuntimeRequest =
  | { type: "auth/requestCode"; email: string }
  | { type: "auth/verify"; email: string; code: string }
  | { type: "auth/signOut" }
  | { type: "openSignIn" }
  /** The language row of a word card: POST /api/translate-lines (guest: cachedOnly). */
  | { type: "lines"; texts: string[]; targetLang: LanguageCode };

export type RuntimeResponse =
  | { ok: true; email?: string; translations?: (string | null)[]; signedIn?: boolean }
  | { ok: false; kind: string; message: string };
