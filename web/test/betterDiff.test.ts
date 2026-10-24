import { describe, expect, it } from "vitest";
import { betterPieces } from "../src/lib/betterDiff";

const changed = (original: string, better: string) =>
  betterPieces(original, better)
    .filter((piece) => piece.changed)
    .map((piece) => piece.text.trim());

describe("betterPieces", () => {
  it("marks only the changed words", () => {
    expect(changed("I already do it.", "I've already done it.")).toEqual(["I've", "done"]);
    expect(changed("Please open the light, it's too dark.", "Please turn on the light. It's too dark.")).toEqual([
      "turn",
      "on",
    ]);
  });

  it("joins back to the fixed text", () => {
    const better = "Can you  lend me your pen?";
    expect(betterPieces("Can you borrow me your pen?", better).map((piece) => piece.text).join("")).toBe(better);
  });

  it("ignores case, punctuation and curly apostrophes", () => {
    expect(changed("i don’t know", "I don't know.")).toEqual([]);
  });
});
