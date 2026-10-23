"use client";
/**
 * The one composer card, like the iOS home (TASK-97): the text box, the language chip and the send button inside,
 * the cat on its top edge. Enter sends, Shift+Enter adds a line.
 */
import { useId, type KeyboardEvent } from "react";
import { MAX_SENTENCE_LENGTH } from "@/lib/textKind";
import { LANGUAGES, type LanguageCode } from "@/lib/languages";
import { IconButton } from "../IconButton";
import { ArrowUp, Close, Globe } from "../icons";
import { PixelCat, type CatMood } from "../PixelCat";

type Props = {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  targetLang: LanguageCode;
  onLanguage: (code: LanguageCode) => void;
  mood: CatMood;
};

export function Composer({ value, onChange, onSubmit, targetLang, onLanguage, mood }: Props) {
  const inputId = useId();
  const tooLong = value.length > MAX_SENTENCE_LENGTH;
  const canSend = value.trim().length > 0 && !tooLong;

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      if (canSend) onSubmit();
    }
  };

  return (
    <div data-enter className="composer pt-16">
      <form
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          if (canSend) onSubmit();
        }}
        className="relative flex flex-col gap-3 rounded-[22px] border border-hairline bg-surface p-4 pb-3 shadow-[0_1px_2px_rgb(0_0_0/0.04),0_10px_30px_-12px_rgb(31_42_38/0.18)] transition-[border-color,box-shadow] focus-within:border-accent focus-within:ring-2 focus-within:ring-accent/15"
      >
        {/* The cat sits on the top edge of the card: its feet 3 px into the border. */}
        <PixelCat
          mood={mood}
          className="cat pointer-events-none absolute right-7 bottom-full w-[86px] translate-y-[3px] sm:right-10 sm:w-[100px]"
        />
        <label htmlFor={inputId} className="sr-only">
          An English word or sentence
        </label>
        <textarea
          id={inputId}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={onKeyDown}
          rows={2}
          placeholder="A word or a sentence in English"
          autoComplete="off"
          autoCapitalize="off"
          spellCheck={false}
          className="field-sizing-content max-h-60 min-h-[3.25rem] w-full resize-none bg-transparent focus-visible:outline-none font-serif text-[1.2rem] leading-snug outline-none placeholder:text-muted/80"
        />
        <div className="flex items-center gap-1">
          <label className="relative inline-flex min-h-11 cursor-pointer items-center">
            <span className="sr-only">Your language</span>
            <span className="pointer-events-none absolute left-3 size-3.5 text-accent">
              <Globe />
            </span>
            <select
              value={targetLang}
              onChange={(event) => onLanguage(event.target.value as LanguageCode)}
              className="h-8 cursor-pointer appearance-none rounded-full bg-chip pr-3 pl-8 text-[0.8rem] font-medium text-accent outline-none"
            >
              {LANGUAGES.map((language) => (
                <option key={language.code} value={language.code}>
                  {language.name}
                </option>
              ))}
            </select>
          </label>
          <span className="flex-1" />
          {value.length > 0 && (
            <>
              <span className={`text-xs tabular-nums ${tooLong ? "font-semibold text-error" : "text-muted"}`}>
                {tooLong ? "Make it shorter. " : ""}
                {value.length} / {MAX_SENTENCE_LENGTH}
              </span>
              <IconButton variant="plain" compact label="Clear" icon={<Close />} onClick={() => onChange("")} />
            </>
          )}
          <button
            type="submit"
            aria-label="Explain"
            title="Explain"
            disabled={!canSend}
            className="send group grid size-11 cursor-pointer place-items-center rounded-full disabled:cursor-default"
          >
            <span className="grid size-10 place-items-center rounded-full bg-accent text-on-accent shadow-sm transition-[transform,opacity] duration-150 group-active:scale-95 group-disabled:opacity-35 motion-reduce:transition-none">
              <ArrowUp className="size-[18px]" />
            </span>
          </button>
        </div>
      </form>
    </div>
  );
}
