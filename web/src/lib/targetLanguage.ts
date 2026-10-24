"use client";
/** The target language picked last on this browser (localStorage), read with useSyncExternalStore. */
import { useCallback, useSyncExternalStore } from "react";
import { DEFAULT_LANGUAGE, languageFromSaved, type LanguageCode } from "./languages";

const KEY = "saynata.targetLang";
const CHANGED = "saynata:target-lang";

function read(): LanguageCode {
  try {
    return languageFromSaved(localStorage.getItem(KEY));
  } catch {
    return DEFAULT_LANGUAGE; // storage blocked, a private window
  }
}

function subscribe(onChange: () => void): () => void {
  window.addEventListener("storage", onChange);
  window.addEventListener(CHANGED, onChange);
  return () => {
    window.removeEventListener("storage", onChange);
    window.removeEventListener(CHANGED, onChange);
  };
}

export function useTargetLanguage(): [LanguageCode, (code: LanguageCode) => void] {
  const language = useSyncExternalStore(subscribe, read, () => DEFAULT_LANGUAGE);
  const set = useCallback((code: LanguageCode) => {
    try {
      localStorage.setItem(KEY, code);
    } catch {
      // Storage blocked: the choice cannot be kept, so the default stays.
    }
    window.dispatchEvent(new Event(CHANGED));
  }, []);
  return [language, set];
}
