import { describe, expect, it } from "vitest";
import { cardTop, iconPosition, placeCard } from "../src/content/placement";

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

describe("placeCard", () => {
  it("opens below the selection, at its left edge, and keeps its top while it grows", () => {
    const placement = placeCard({ left: 120, right: 400, top: 100, bottom: 120 }, 340, view);
    expect(placement).toMatchObject({ left: 120, side: "below", edge: 128 });
    expect(cardTop(placement, 200)).toBe(128);
    expect(cardTop(placement, 420)).toBe(128);
  });

  it("never leaves the right edge, and adds the page scroll", () => {
    const placement = placeCard({ left: 900, right: 990, top: 100, bottom: 120 }, 340, { ...view, scrollY: 1000 });
    expect(placement.left).toBe(1000 - 340 - 8);
    expect(placement.edge).toBe(1128);
  });

  it("opens above near the bottom, and keeps its bottom while it grows", () => {
    const placement = placeCard({ left: 120, right: 400, top: 650, bottom: 670 }, 340, view);
    expect(placement).toMatchObject({ side: "above", edge: 642 });
    expect(cardTop(placement, 200)).toBe(442);
    expect(cardTop(placement, 300)).toBe(342);
  });

  it("once the top is frozen, a taller card keeps it (a row opens downward)", () => {
    const placement = placeCard({ left: 120, right: 400, top: 650, bottom: 670 }, 340, view);
    const top = cardTop(placement, 250);
    expect(cardTop(placement, 290, top)).toBe(top);
  });

  it("limits the height to the room on its side and to 70% of the window", () => {
    expect(placeCard({ left: 0, right: 0, top: 650, bottom: 670 }, 340, view).maxHeight).toBe(560);
    expect(placeCard({ left: 0, right: 0, top: 300, bottom: 320 }, 340, view).maxHeight).toBe(464);
  });
});
