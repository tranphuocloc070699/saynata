"use client";
/** Reads English aloud with the browser's own voices (Web Speech API), like the app's SpeechPlayer. */
import { useCallback, useEffect, useState } from "react";

export function useSpeech() {
  const [speaking, setSpeaking] = useState<string | null>(null);

  useEffect(() => () => window.speechSynthesis?.cancel(), []);

  const toggle = useCallback(
    (text: string) => {
      const synth = window.speechSynthesis;
      if (!synth) return;
      synth.cancel();
      if (speaking === text) {
        setSpeaking(null);
        return;
      }
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = "en-US";
      utterance.rate = 0.92;
      utterance.onend = () => setSpeaking((current) => (current === text ? null : current));
      utterance.onerror = utterance.onend;
      setSpeaking(text);
      synth.speak(utterance);
    },
    [speaking],
  );

  return { speaking, toggle };
}
