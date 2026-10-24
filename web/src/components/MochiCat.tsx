"use client";
/**
 * The cat on the composer card: one Mochi sprite sheet, picked once per page visit (lib/mochi.ts), looped by CSS
 * steps() on background-position (globals.css .mochi), so no timer re-renders the page. It pauses while the tab
 * is hidden and shows only frame 1 with reduced motion. When /cats/ has no file (local dev, or before
 * scripts/upload-web-cats.sh ran), it falls back to our own PixelCat.
 */
import { useEffect, useRef, useState, type CSSProperties } from "react";
import { gsap, useGSAP, withMotion } from "@/lib/motion";
import { sheetAvailable, sheetStyle, visitCat, type CatSheet } from "@/lib/mochi";
import { PixelCat, type CatMood } from "./PixelCat";

type Props = {
  /** Only the fallback cat uses it; the Mochi sheet stays the same for the whole visit. */
  mood: CatMood;
  /** Position on the card; the size comes from the sheet (32 px frames at 3x). */
  className?: string;
  /** The fallback's width classes. */
  fallbackClassName?: string;
};

type Shown = { kind: "pending" } | { kind: "sheet"; sheet: CatSheet } | { kind: "fallback" };

export function MochiCat({ mood, className = "", fallbackClassName = "" }: Props) {
  // "pending" on the server and on the first client render, so the HTML matches; the pick happens in the browser.
  const [shown, setShown] = useState<Shown>({ kind: "pending" });
  const cat = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const sheet = visitCat();
    let live = true;
    void sheetAvailable(sheet).then((ok) => {
      if (live) setShown(ok ? { kind: "sheet", sheet } : { kind: "fallback" });
    });
    return () => {
      live = false;
    };
  }, []);

  // Pause the loop while the tab is hidden: an attribute on <html> read by the CSS, no React state.
  useEffect(() => {
    const root = document.documentElement;
    const sync = () => root.toggleAttribute("data-tab-hidden", document.hidden);
    sync();
    document.addEventListener("visibilitychange", sync);
    return () => {
      document.removeEventListener("visibilitychange", sync);
      root.removeAttribute("data-tab-hidden");
    };
  }, []);

  // The cat drops onto the card once, when it first appears.
  useGSAP(
    () => {
      if (!cat.current) return;
      withMotion(() => {
        gsap.from(cat.current, { y: -26, opacity: 0, duration: 0.75, ease: "bounce.out" });
      });
    },
    { dependencies: [shown.kind] },
  );

  if (shown.kind === "pending") return null;
  if (shown.kind === "fallback") {
    return (
      <span ref={cat} aria-hidden="true" className={`pointer-events-none ${className} ${fallbackClassName}`}>
        <PixelCat mood={mood} className="block w-full" />
      </span>
    );
  }
  return (
    <span
      ref={cat}
      aria-hidden="true"
      data-cat={shown.sheet.kind}
      className={`mochi pointer-events-none ${className}`}
      style={sheetStyle(shown.sheet) as CSSProperties}
    />
  );
}
