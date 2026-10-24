import { readFileSync } from "node:fs";
import { beforeEach, describe, expect, it } from "vitest";
import {
  CAT_SHEETS,
  pickCat,
  resetSheetChecks,
  resetVisitCat,
  sheetAvailable,
  sheetStyle,
  visitCat,
} from "../src/lib/mochi";

/** A random() that returns the given values in order. */
const sequence = (...values: number[]) => {
  let i = 0;
  return () => values[i++ % values.length];
};

describe("pickCat", () => {
  it("gives the five choices equal shares of [0, 1)", () => {
    expect(pickCat(() => 0).file).toBe("Dance.png");
    expect(pickCat(() => 0.2).file).toBe("Sleep.png");
    expect(pickCat(() => 0.4).file).toBe("Excited.png");
    expect(pickCat(() => 0.6).file).toBe("catsick1.png");
    expect(pickCat(sequence(0.8, 0)).kind).toBe("box");
  });

  it("picks Box1, Box2 or Box3 for Box", () => {
    expect(pickCat(sequence(0.9, 0)).file).toBe("Box1.png");
    expect(pickCat(sequence(0.9, 0.5)).file).toBe("Box2.png");
    expect(pickCat(sequence(0.9, 0.99)).file).toBe("Box3.png");
  });
});

describe("visitCat", () => {
  beforeEach(() => resetVisitCat());

  it("keeps the first pick for the whole visit, whatever random() says later", () => {
    const first = visitCat(() => 0.2);
    for (const r of [0, 0.4, 0.6, 0.9]) expect(visitCat(() => r)).toBe(first);
    expect(first.file).toBe("Sleep.png");
  });

  it("picks again only on a new visit (a reload runs the module again)", () => {
    expect(visitCat(() => 0).file).toBe("Dance.png");
    resetVisitCat();
    expect(visitCat(() => 0.4).file).toBe("Excited.png");
  });
});

describe("sheetAvailable (fallback)", () => {
  beforeEach(() => resetSheetChecks());

  it("is false when /cats/ has no file, so the own pixel cat is shown", async () => {
    expect(await sheetAvailable(CAT_SHEETS.dance, async () => false)).toBe(false);
  });

  it("is false when loading throws", async () => {
    expect(await sheetAvailable(CAT_SHEETS.sick, () => Promise.reject(new Error("offline")))).toBe(false);
  });

  it("asks once per file", async () => {
    const urls: string[] = [];
    const load = async (url: string) => (urls.push(url), true);
    await sheetAvailable(CAT_SHEETS.box2, load);
    await sheetAvailable(CAT_SHEETS.box2, load);
    expect(urls).toEqual(["/cats/Box2.png"]);
  });
});

describe("sheetStyle", () => {
  it("sizes one 32 px frame at 3x and the whole strip, and times the loop from frames and fps", () => {
    expect(sheetStyle(CAT_SHEETS.excited)).toEqual({
      "--mochi-size": "96px",
      "--mochi-strip": "1152px",
      "--mochi-frames": "12",
      "--mochi-duration": "1.500s",
      backgroundImage: 'url("/cats/Excited.png")',
    });
  });
});

describe("globals.css", () => {
  const css = readFileSync(new URL("../src/app/globals.css", import.meta.url), "utf8");
  const reduced = css.slice(css.indexOf("@media (prefers-reduced-motion: reduce)"));

  it("loops with steps() and square pixels", () => {
    expect(css).toMatch(/\.mochi \{[^}]*steps\(var\(--mochi-frames\)\)[^}]*\}/);
    expect(css).toMatch(/\.mochi \{[^}]*image-rendering: pixelated/);
  });

  it("shows only frame 1 with reduced motion", () => {
    expect(reduced).toMatch(/\.mochi \{\s*animation: none;\s*background-position: 0 0;/);
  });

  it("pauses while the tab is hidden", () => {
    expect(css).toMatch(/:root\[data-tab-hidden\] \.mochi \{\s*animation-play-state: paused;/);
  });
});
