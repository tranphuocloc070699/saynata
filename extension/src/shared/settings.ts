/**
 * The two settings the app has (doc-4 "Show translation setting"): the target language and how the
 * target-language row starts. Saved in chrome.storage.sync, so they follow the user's browser profile.
 */
import { DEFAULT_LANGUAGE, isLanguageCode, type LanguageCode } from "./languages";

export type ShowTranslationMode = "collapsed" | "open" | "remember";

export const SHOW_TRANSLATION_MODES: { mode: ShowTranslationMode; label: string }[] = [
  { mode: "collapsed", label: "Collapsed" },
  { mode: "open", label: "Always open" },
  { mode: "remember", label: "Remember last choice" },
];

export interface Settings {
  targetLang: LanguageCode;
  showTranslation: ShowTranslationMode;
  /** The last open or closed state the user set on any target-language row (used by "remember"). */
  lastRowOpen: boolean;
}

export const DEFAULT_SETTINGS: Settings = { targetLang: DEFAULT_LANGUAGE, showTranslation: "collapsed", lastRowOpen: false };

/** The part of chrome.storage.StorageArea the settings use, so tests can pass a fake. */
export interface KeyValueArea {
  get(keys: string[]): Promise<Record<string, unknown>>;
  set(items: Record<string, unknown>): Promise<void>;
}

const KEYS = ["targetLang", "showTranslation", "lastRowOpen"];

/** Reads the settings; a missing or broken value falls back to its default. */
export async function loadSettings(area: KeyValueArea): Promise<Settings> {
  const stored = await area.get(KEYS);
  return {
    targetLang: isLanguageCode(stored.targetLang) ? stored.targetLang : DEFAULT_SETTINGS.targetLang,
    showTranslation: isMode(stored.showTranslation) ? stored.showTranslation : DEFAULT_SETTINGS.showTranslation,
    lastRowOpen: typeof stored.lastRowOpen === "boolean" ? stored.lastRowOpen : DEFAULT_SETTINGS.lastRowOpen,
  };
}

export async function saveSettings(area: KeyValueArea, change: Partial<Settings>): Promise<void> {
  await area.set(change);
}

/** How a new target-language row starts (iOS TranslationRowPreference.startsOpen). */
export function rowStartsOpen(settings: Settings): boolean {
  switch (settings.showTranslation) {
    case "collapsed":
      return false;
    case "open":
      return true;
    case "remember":
      return settings.lastRowOpen;
  }
}

function isMode(value: unknown): value is ShowTranslationMode {
  return SHOW_TRANSLATION_MODES.some((option) => option.mode === value);
}
