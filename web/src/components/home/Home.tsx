"use client";
/**
 * The homepage tool (TASK-102.1). The query lives in the address (/?q=...), so a result can be shared, reloaded
 * and reached with Back. The search itself is in lib/explain.ts; this file holds the state and the motion.
 */
import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { SaynataApi, type Suggestion } from "@/lib/api";
import { fetchResult, startState, type Result } from "@/lib/explain";
import { ENTER, gsap, useGSAP, withMotion } from "@/lib/motion";
import { useTargetLanguage } from "@/lib/targetLanguage";
import { Alert } from "../icons";
import type { CatMood } from "../PixelCat";
import { Composer } from "./Composer";
import { SentenceCard } from "./SentenceCard";
import { WordCard } from "./WordCard";

const api = new SaynataApi("");
const SUGGEST_DELAY_MS = 250;
const SUGGEST_MAX_LENGTH = 40;
/** Cached answers, so a guest can try them (TASK-83.11 prefill). */
const SAMPLES = ["give up", "run", "Hold on a second.", "Looks good."];

export function Home() {
  const router = useRouter();
  const pathname = usePathname();
  const query = useSearchParams().get("q")?.trim() ?? "";
  const [targetLang, setTargetLang] = useTargetLanguage();
  /** Bumped by a submit of the same text, so "try again" runs the search again. */
  const [attempt, setAttempt] = useState(0);

  const [text, setText] = useState(query);
  const [result, setResult] = useState<Result>(() => startState(query));
  const [suggested, setSuggested] = useState<{ prefix: string; list: Suggestion[] }>({ prefix: "", list: [] });
  const root = useRef<HTMLDivElement>(null);

  // A new search (address, language or retry): show its first state now, during render, not in an effect.
  const runKey = `${query}|${targetLang}|${attempt}`;
  const [shownKey, setShownKey] = useState(runKey);
  const [shownQuery, setShownQuery] = useState(query);
  if (shownKey !== runKey) {
    setShownKey(runKey);
    setResult(startState(query));
    if (shownQuery !== query) {
      setShownQuery(query);
      setText(query);
    }
  }

  // ...and fetch it. Every state from here on comes from a network callback.
  useEffect(() => {
    const controller = new AbortController();
    void fetchResult(api, query, targetLang, setResult, controller.signal);
    return () => controller.abort();
  }, [query, targetLang, attempt]);

  const submit = (value: string) => {
    const clean = value.trim();
    setText(clean);
    if (clean === query) setAttempt((n) => n + 1);
    else router.push(`${pathname}?q=${encodeURIComponent(clean)}`, { scroll: false });
  };

  // Suggestions while typing: only from the bundle and the cache, so they cost nothing (GET /api/suggest).
  const prefix = text.trim();
  const wantsSuggestions = prefix.length > 0 && prefix.length <= SUGGEST_MAX_LENGTH && prefix !== query;
  useEffect(() => {
    if (!wantsSuggestions) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      api
        .suggest(prefix, targetLang, controller.signal)
        .then((list) => setSuggested({ prefix, list }))
        .catch(() => undefined);
    }, SUGGEST_DELAY_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [prefix, targetLang, wantsSuggestions]);
  const suggestions =
    wantsSuggestions && suggested.prefix === prefix
      ? suggested.list.filter((s) => s.text.toLowerCase() !== prefix.toLowerCase())
      : [];

  // Motion 1: the page enters (the intro in page.tsx too, so the whole document is searched), then the cat
  // drops onto the card and the send button pops in. Runs once.
  useGSAP(
    () => {
      withMotion(() => {
        gsap
          .timeline({ defaults: { ease: "power3.out" } })
          .fromTo(gsap.utils.toArray("[data-enter]", document), { opacity: 0, y: 18 }, { opacity: 1, y: 0, duration: 0.6, stagger: 0.08 })
          .from(".cat", { y: -26, opacity: 0, duration: 0.75, ease: "bounce.out" }, 0.3)
          .from(".send", { scale: 0.6, opacity: 0, duration: 0.4, ease: "back.out(2)" }, 0.45);
      });
    },
    { scope: root },
  );

  // Motion 2: new parts of a result rise in, one after another. Parts already shown are left alone, so the
  // stream finishing (the words and the language row appear) does not replay the card.
  useGSAP(
    () => {
      const fresh = gsap.utils.toArray<HTMLElement>("[data-reveal]:not([data-shown])");
      fresh.forEach((el) => el.setAttribute("data-shown", ""));
      if (fresh.length === 0) return;
      withMotion(() => {
        gsap.from(fresh, { autoAlpha: 0, y: ENTER.y, duration: ENTER.duration, ease: ENTER.ease, stagger: ENTER.stagger });
      });
    },
    { scope: root, dependencies: [result] },
  );

  const chips = suggestions.length > 0 ? suggestions.map((s) => s.text) : result.kind === "idle" && !text ? SAMPLES : [];

  // Motion 3: suggestions and sample chips fan out quickly when the list changes.
  useGSAP(
    () => {
      withMotion(() => {
        gsap.from(".chip-item", { autoAlpha: 0, y: 6, duration: 0.25, ease: "power1.out", stagger: 0.03 });
      });
    },
    { scope: root, dependencies: [chips.join("\n")], revertOnUpdate: true },
  );

  const mood: CatMood =
    result.kind === "loading" || (result.kind === "sentence" && !result.explanation)
      ? "busy"
      : text.length > 0 || result.kind !== "idle"
        ? "awake"
        : "asleep";

  return (
    <div ref={root} className="flex w-full flex-col gap-5">
      <Composer
        value={text}
        onChange={setText}
        onSubmit={() => submit(text)}
        targetLang={targetLang}
        onLanguage={setTargetLang}
        mood={mood}
      />

      {chips.length > 0 && (
        <ul aria-label={suggestions.length > 0 ? "Suggestions" : "Try one"} className="-mt-1 flex flex-wrap justify-center gap-2">
          {chips.map((chip) => (
            <li key={chip} className="chip-item">
              <button
                type="button"
                onClick={() => submit(chip)}
                className="min-h-9 cursor-pointer rounded-full border border-hairline bg-surface px-3.5 text-sm text-ink-2 transition-colors hover:border-accent/50 hover:text-accent"
              >
                {chip}
              </button>
            </li>
          ))}
        </ul>
      )}

      <div aria-live="polite">
        {result.kind === "loading" && <WordSkeleton />}
        {result.kind === "error" && (
          <p data-reveal key={result.message} role="alert" className="flex items-start gap-2 px-2 text-error">
            <Alert className="mt-0.5 size-4 shrink-0" />
            <span>{result.message}</span>
          </p>
        )}
      </div>
      {result.kind === "word" && <WordCard key={result.query} word={result.word} targetLang={targetLang} />}
      {result.kind === "sentence" && (
        <SentenceCard
          key={result.query}
          text={result.query}
          meaning={result.meaning}
          explanation={result.explanation}
          targetLang={targetLang}
          onWord={submit}
        />
      )}
    </div>
  );
}

function WordSkeleton() {
  return (
    <div className="flex flex-col gap-3 rounded-[20px] border border-hairline bg-surface p-5" aria-label="Looking it up">
      <span className="h-7 w-28 animate-pulse rounded-lg bg-meaning motion-reduce:animate-none" />
      <span className="h-16 animate-pulse rounded-2xl bg-meaning motion-reduce:animate-none" />
      <span className="h-16 animate-pulse rounded-2xl bg-meaning motion-reduce:animate-none" />
    </div>
  );
}
