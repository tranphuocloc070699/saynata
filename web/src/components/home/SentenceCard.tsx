"use client";
/**
 * The sentence result, like the iOS SentenceCard (TASK-89.2): the original with tappable words (a tap opens the
 * word in WordSheet, over the result), the simple summary ("In simple words") that grows while it streams, the key
 * words, then the target language closed and last.
 */
import { useState } from "react";
import type { Explanation } from "@/lib/api";
import { languageName, type LanguageCode } from "@/lib/languages";
import { useSpeech } from "@/lib/speech";
import { FoldedRow } from "../FoldedRow";
import { IconButton } from "../IconButton";
import { Check, Copy, Speaker, Stop } from "../icons";

type Props = {
  text: string;
  /** The meaning so far; null until the first piece arrives. */
  meaning: string | null;
  /** The full answer, once the stream is done. */
  explanation: Explanation | null;
  targetLang: LanguageCode;
  onWord: (word: string) => void;
};

export function SentenceCard({ text, meaning, explanation, targetLang, onWord }: Props) {
  const speech = useSpeech();
  const [copied, setCopied] = useState(false);
  const streaming = explanation === null;
  const summary = explanation?.meaning ?? meaning;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard refused (an old browser or no permission): nothing to undo.
    }
  };

  return (
    <div className="flex flex-col gap-1">
      <article className="flex flex-col gap-3 rounded-[20px] border border-hairline bg-surface p-4 sm:p-5">
        <div data-reveal className="flex items-start gap-2">
          <p className="flex-1 font-serif text-[1.25rem] leading-snug">
            {tokens(text).map((token, i) =>
              token.word ? (
                <button
                  key={i}
                  type="button"
                  onClick={() => onWord(token.word!)}
                  className="cursor-pointer rounded underline decoration-muted decoration-dotted underline-offset-4 hover:bg-chip hover:text-accent"
                >
                  {token.text}
                </button>
              ) : (
                <span key={i}>{token.text}</span>
              ),
            )}
          </p>
          <ListenButton text={text} speech={speech} />
        </div>
        <div className="h-px bg-hairline" />
        <div data-reveal className="flex flex-col gap-1" aria-live="polite" aria-busy={streaming}>
          <div className="flex items-start gap-2">
            {summary ? (
              <p className={`flex-1 font-serif text-[1.0625rem] leading-relaxed ${streaming ? "stream-caret" : ""}`}>
                {summary}
              </p>
            ) : (
              <div className="flex flex-1 flex-col gap-2 py-1.5" aria-label="Writing the explanation">
                <span className="h-3 w-11/12 animate-pulse rounded bg-meaning motion-reduce:animate-none" />
                <span className="h-3 w-2/3 animate-pulse rounded bg-meaning motion-reduce:animate-none" />
              </div>
            )}
            {explanation && <ListenButton text={explanation.meaning} speech={speech} />}
          </div>
          <span className="text-xs text-muted">In simple words</span>
        </div>
        {explanation && explanation.words.length > 0 && (
          <div data-reveal className="flex flex-col gap-1">
            <span className="text-[0.7rem] font-semibold tracking-wider text-muted uppercase">Words</span>
            <ul className="flex flex-col">
              {explanation.words.map((word) => (
                <li key={word.word}>
                  <button
                    type="button"
                    onClick={() => onWord(word.word)}
                    className="flex min-h-11 w-full cursor-pointer items-baseline gap-2 rounded-lg px-1 text-left hover:bg-meaning"
                  >
                    <span className="font-semibold">{word.word}</span>
                    <span className="text-xs text-muted">{word.partOfSpeech}</span>
                    <span className="flex-1 font-serif text-ink-2">{word.meaning}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {explanation && targetLang !== "en" && explanation.translation && (
          <div data-reveal>
            <FoldedRow title={languageName(targetLang)}>
              <p className="font-serif">{explanation.translation}</p>
            </FoldedRow>
          </div>
        )}
      </article>
      <div className="flex justify-end">
        <IconButton
          variant="plain"
          label={copied ? "Copied" : "Copy"}
          icon={copied ? <Check /> : <Copy />}
          active={copied}
          onClick={copy}
        />
      </div>
    </div>
  );
}

function ListenButton({ text, speech }: { text: string; speech: ReturnType<typeof useSpeech> }) {
  const playing = speech.speaking === text;
  return (
    <IconButton
      compact
      label={playing ? "Stop" : "Listen"}
      icon={playing ? <Stop /> : <Speaker />}
      active={playing}
      onClick={() => speech.toggle(text)}
      className="-my-1.5"
    />
  );
}

/** Splits a sentence into words (tappable) and the spaces and marks between them. */
export function tokens(text: string): { text: string; word?: string }[] {
  return text.split(/([\p{L}][\p{L}'-]*)/u).filter(Boolean).map((part) =>
    /^[\p{L}]/u.test(part) ? { text: part, word: part.toLowerCase() } : { text: part },
  );
}
