import { describe, expect, it } from "vitest";
import { cardPosition, iconPosition } from "../src/content/placement";

const view = { width: 1000, height: 800, scrollX: 0, scrollY: 0 };
const card = { width: 340, height: 300 };

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

describe("cardPosition", () => {
  it("opens below the selection, at its left edge", () => {
    expect(cardPosition({ left: 120, right: 400, top: 100, bottom: 120 }, card, view)).toEqual({ left: 120, top: 128 });
  });

  it("never leaves the right edge", () => {
    expect(cardPosition({ left: 900, right: 990, top: 100, bottom: 120 }, card, view).left).toBe(1000 - 340 - 8);
  });

  it("opens above when there is no room below and more room above", () => {
    expect(cardPosition({ left: 120, right: 400, top: 650, bottom: 670 }, card, view).top).toBe(650 - 8 - 300);
  });

  it("stays below when below has more room, even if the card is taller", () => {
    const tall = { width: 340, height: 700 };
    expect(cardPosition({ left: 120, right: 400, top: 300, bottom: 320 }, tall, view).top).toBe(328);
  });
});
