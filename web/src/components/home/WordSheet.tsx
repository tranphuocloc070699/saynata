"use client";
/**
 * The word card over a sentence result, like the iOS SentenceWordSheet: a tap on a word in the sentence opens it,
 * and the input, the address (?q=) and the sentence stay as they are. A native <dialog> opened with showModal()
 * gives the focus trap, Esc and the backdrop; a click on the backdrop closes it too.
 * A sheet from the bottom on a phone, a centered card on a wider screen.
 */
import { useEffect, useRef, useState } from "react";
import { SaynataApi } from "@/lib/api";
import { fetchResult, startState, type Result } from "@/lib/explain";
import type { LanguageCode } from "@/lib/languages";
import { gsap, useGSAP, withMotion } from "@/lib/motion";
import { IconButton } from "../IconButton";
import { Alert, Close } from "../icons";
import { WordCard, WordSkeleton } from "./WordCard";

const api = new SaynataApi("");

type Props = {
  /** The word to show; null when the sheet is closed. */
  word: string | null;
  targetLang: LanguageCode;
  onClose: () => void;
};

export function WordSheet({ word, targetLang, onClose }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const element = dialog.current;
    if (!element) return;
    if (word && !element.open) element.showModal();
    if (!word && element.open) element.close();
  }, [word]);

  useGSAP(
    () => {
      if (!word) return;
      withMotion(() => {
        gsap.from(".sheet-panel", { y: 28, autoAlpha: 0, duration: 0.32, ease: "power3.out" });
      });
    },
    { scope: dialog, dependencies: [word] },
  );

  return (
    <dialog
      ref={dialog}
      aria-label={word ? `The word ${word}` : "Word"}
      onClose={onClose}
      // A click outside the panel lands on the <dialog> itself (its ::backdrop area).
      onClick={(event) => {
        if (event.target === event.currentTarget) event.currentTarget.close();
      }}
      className="m-0 mt-auto max-h-[88dvh] w-full max-w-none overflow-visible bg-transparent p-0 text-ink backdrop:bg-ink/35 backdrop:backdrop-blur-[2px] sm:m-auto sm:max-w-[600px]"
    >
      {word && (
        <div className="sheet-panel flex max-h-[88dvh] flex-col rounded-t-[24px] bg-bg shadow-2xl sm:rounded-[24px]">
          <div className="flex items-center justify-between px-3 pt-2">
            <span aria-hidden="true" className="mx-auto h-1.5 w-10 rounded-full bg-hairline sm:invisible" />
          </div>
          <div className="flex justify-end px-2">
            <IconButton variant="plain" label="Close" icon={<Close />} onClick={() => dialog.current?.close()} />
          </div>
          <div className="overflow-y-auto overscroll-contain px-3 pb-5 sm:px-5">
            <WordLookup key={`${word}|${targetLang}`} word={word} targetLang={targetLang} />
          </div>
        </div>
      )}
    </dialog>
  );
}

/** Looks the word up with the same call as a search from the box (lib/explain.ts). */
function WordLookup({ word, targetLang }: { word: string; targetLang: LanguageCode }) {
  const [result, setResult] = useState<Result>(() => startState(word));

  useEffect(() => {
    const controller = new AbortController();
    void fetchResult(api, word, targetLang, setResult, controller.signal);
    return () => controller.abort();
  }, [word, targetLang]);

  if (result.kind === "word") return <WordCard word={result.word} targetLang={targetLang} />;
  if (result.kind === "error") {
    return (
      <p role="alert" className="flex items-start gap-2 px-2 py-4 text-error">
        <Alert className="mt-0.5 size-4 shrink-0" />
        <span>{result.message}</span>
      </p>
    );
  }
  return <WordSkeleton />;
}
