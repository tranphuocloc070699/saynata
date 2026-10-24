"use client";
/**
 * The word result, the web version of the iOS word card: headword, IPA and Listen, then one card per sense
 * (number, simple explanation, level chip, picture), with Examples, How to use it and the target language
 * as closed rows, the target language last (product rule: explain first, translate last).
 */
import type { Sense, WordResponse } from "@/lib/api";
import { isAllowedMedia, isClip } from "@/lib/media";
import { languageName, type LanguageCode } from "@/lib/languages";
import { useSpeech } from "@/lib/speech";
import { FoldedRow } from "../FoldedRow";
import { IconButton } from "../IconButton";
import { Speaker, Stop } from "../icons";

const LEVEL_COLOR: Record<string, string> = {
  easy: "text-level-easy bg-level-easy/15",
  medium: "text-level-medium bg-level-medium/15",
  harder: "text-level-harder bg-level-harder/15",
};

export function WordCard({ word, targetLang }: { word: WordResponse; targetLang: LanguageCode }) {
  const speech = useSpeech();
  return (
    <article className="flex flex-col gap-3 rounded-[20px] border border-hairline bg-surface p-4 sm:p-5">
      <header data-reveal className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h2 className="text-[2rem] leading-tight font-semibold tracking-tight">{word.word}</h2>
        {word.ipa && <span className="text-muted">/{word.ipa.replace(/^\/|\/$/g, "")}/</span>}
        <IconButton
          compact
          label={speech.speaking === word.word ? "Stop" : "Listen"}
          icon={speech.speaking === word.word ? <Stop /> : <Speaker />}
          active={speech.speaking === word.word}
          onClick={() => speech.toggle(word.word)}
        />
      </header>
      {word.entry.senses.map((sense, index) => (
        <SenseCard key={index} number={index + 1} sense={sense} targetLang={targetLang} speak={speech.toggle} />
      ))}
    </article>
  );
}

function SenseCard({
  number,
  sense,
  targetLang,
  speak,
}: {
  number: number;
  sense: Sense;
  targetLang: LanguageCode;
  speak: (text: string) => void;
}) {
  const media = sense.imageUrl && isAllowedMedia(sense.imageUrl) ? sense.imageUrl : null;
  return (
    <section data-reveal className="rounded-2xl border border-hairline px-4 pt-3">
      <div className="flex items-start gap-3 pb-3">
        <span className="mt-0.5 grid size-6 shrink-0 place-items-center rounded-full bg-chip text-xs font-bold text-accent">
          {number}
        </span>
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <p className="font-serif text-[1.0625rem] leading-snug">{sense.simpleDefinition}</p>
          <span className="self-start rounded-full bg-chip px-2.5 py-0.5 text-xs font-medium text-accent">
            {sense.pos} · {sense.cefr}
          </span>
          {media && <SenseMedia url={media} />}
        </div>
      </div>
      {sense.examples.length > 0 && (
        <FoldedRow title="Examples">
          <ul className="flex flex-col gap-2">
            {sense.examples.map((example) => (
              <li key={example.text} className="flex items-start gap-2">
                <span
                  className={`mt-0.5 shrink-0 rounded-md px-1.5 text-xs font-semibold ${LEVEL_COLOR[example.level] ?? LEVEL_COLOR.easy}`}
                >
                  {example.level}
                </span>
                <p className="flex-1 font-serif text-[0.95rem] italic">{example.text}</p>
                <button
                  type="button"
                  aria-label="Listen to the example"
                  title="Listen"
                  onClick={() => speak(example.text)}
                  className="-my-3 grid size-11 shrink-0 cursor-pointer place-items-center text-muted hover:text-ink"
                >
                  <Speaker className="size-4" />
                </button>
              </li>
            ))}
          </ul>
        </FoldedRow>
      )}
      {sense.usage.length > 0 && (
        <FoldedRow title="How to use it">
          <ul className="flex flex-col gap-2">
            {sense.usage.map((usage) => (
              <li key={usage.pattern}>
                <p className="font-medium">{usage.pattern}</p>
                <p className="font-serif text-[0.95rem] text-ink-2 italic">{usage.example}</p>
              </li>
            ))}
          </ul>
        </FoldedRow>
      )}
      {/* The dictionary has Vietnamese only; other languages come with the on-device translation later. */}
      {targetLang === "vi" && sense.vietnamese && (
        <FoldedRow title={languageName(targetLang)}>
          <p className="font-serif">{sense.vietnamese}</p>
        </FoldedRow>
      )}
    </section>
  );
}

function SenseMedia({ url }: { url: string }) {
  const frame = "mt-1 aspect-video w-full max-w-[240px] rounded-xl bg-meaning object-cover";
  return isClip(url) ? (
    <video src={url} className={frame} muted loop autoPlay playsInline aria-hidden="true" />
  ) : (
    // eslint-disable-next-line @next/next/no-img-element -- a picture from our media server, already sized
    <img src={url} alt="" className={frame} loading="lazy" decoding="async" />
  );
}

/** The word card while it loads. */
export function WordSkeleton() {
  return (
    <div className="flex flex-col gap-3 rounded-[20px] border border-hairline bg-surface p-5" aria-label="Looking it up">
      <span className="h-7 w-28 animate-pulse rounded-lg bg-meaning motion-reduce:animate-none" />
      <span className="h-16 animate-pulse rounded-2xl bg-meaning motion-reduce:animate-none" />
      <span className="h-16 animate-pulse rounded-2xl bg-meaning motion-reduce:animate-none" />
    </div>
  );
}
