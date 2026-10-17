import { describe, expect, it } from "vitest";
import { anchorRect, cardMaxHeight, cardTop, iconPosition, placeCard } from "../src/content/placement";

const view = { width: 1000, height: 800, scrollX: 0, scrollY: 0 };

describe("iconPosition", () => {
  it("sits just below the end of the last line", () => {
    expect(iconPosition({ left: 100, right: 300, top: 200, bottom: 220 }, view)).toEqual({ left: 286, top: 224 });
  });

  it("adds the page scroll", () => {
    expect(iconPosition({ left: 100, right: 300, top: 200, bottom: 220 }, { ...view, scrollY: 500 }).top).toBe(724);
  });

  it("stays inside the right edge and moves above at the bottom", () => {
    const position = iconPosition({ left: 900, right: 1000, top: 770, bottom: 790 }, view);
    expect(position.left).toBe(1000 - 28 - 8);
    expect(position.top).toBe(770 - 28 - 4);
  });
});

describe("placeCard: beside the text, centred on it", () => {
  const card = { width: 340, height: 200 };
  // A short phrase in the middle-left of a 1000x800 window: centre y = 310.
  const phrase = { left: 120, right: 400, top: 300, bottom: 320 };

  it("goes to the right of the text, its centre level with the text's centre", () => {
    const placement = placeCard(phrase, phrase, card, view);
    expect(placement).toMatchObject({ side: "right", left: 410 });
    expect(cardTop(placement, 200)).toBe(210);
    expect(cardTop(placement, 200) + 100).toBe(310);
  });

  it("goes to the left when the right has no room", () => {
    const right = { left: 600, right: 760, top: 300, bottom: 320 };
    const placement = placeCard(right, right, card, view);
    expect(placement).toMatchObject({ side: "left", left: 600 - 10 - 340 });
    expect(cardTop(placement, 200)).toBe(210);
  });

  it("goes below, centred on the text, when neither side has room", () => {
    const wide = { left: 300, right: 700, top: 300, bottom: 320 };
    const placement = placeCard(wide, wide, card, view);
    expect(placement).toMatchObject({ side: "below", left: 500 - 170, anchorY: 328 });
    expect(cardTop(placement, 200)).toBe(328);
  });

  it("goes above, centred, when below has less room too", () => {
    const wide = { left: 300, right: 700, top: 700, bottom: 720 };
    const placement = placeCard(wide, wide, card, view);
    expect(placement.side).toBe("above");
    expect(cardTop(placement, 200)).toBe(700 - 8 - 200);
  });

  it("clamps to the window with an 8 px margin, and stays beside the text (never over it)", () => {
    const top = { left: 120, right: 400, top: 10, bottom: 30 };
    const high = placeCard(top, top, card, view);
    expect(cardTop(high, 200)).toBe(8);
    expect(high.left).toBeGreaterThan(top.right);
    const low = { left: 120, right: 400, top: 770, bottom: 790 };
    expect(cardTop(placeCard(low, low, card, view), 200)).toBe(800 - 8 - 200);
  });

  it("uses the last line of a wide multi-line selection, the whole selection when it is narrow", () => {
    const lines = { left: 100, right: 900, top: 300, bottom: 360 };
    const last = { left: 100, right: 380, top: 340, bottom: 360 };
    expect(anchorRect(lines, last, view)).toBe(last);
    const placement = placeCard(lines, last, card, view);
    expect(placement).toMatchObject({ side: "right", left: 390 });
    expect(cardTop(placement, 200)).toBe(250);
    const narrow = { left: 100, right: 400, top: 300, bottom: 360 };
    expect(anchorRect(narrow, last, view)).toBe(narrow);
  });

  it("below a multi-line selection, it starts under the whole selection, not under its last line", () => {
    const lines = { left: 100, right: 900, top: 300, bottom: 360 };
    const last = { left: 100, right: 900, top: 340, bottom: 360 };
    expect(placeCard(lines, last, card, view)).toMatchObject({ side: "below", anchorY: 368 });
  });

  it("adds the page scroll", () => {
    const placement = placeCard(phrase, phrase, card, { ...view, scrollX: 5, scrollY: 1000 });
    expect(placement.left).toBe(415);
    expect(cardTop(placement, 200)).toBe(1210);
  });

  it("once frozen, a taller card keeps its top and scrolls inside at the window's bottom", () => {
    const placement = placeCard(phrase, phrase, card, view);
    const top = cardTop(placement, 200);
    expect(cardTop(placement, 380, top)).toBe(top);
    expect(cardMaxHeight(placement, 500)).toBe(800 - 8 - 500);
  });

  it("caps the height at 560 px", () => {
    const placement = placeCard(phrase, phrase, card, view);
    expect(cardMaxHeight(placement, 8)).toBe(560);
  });
});
