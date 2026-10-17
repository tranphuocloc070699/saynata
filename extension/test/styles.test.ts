import { describe, expect, it } from "vitest";
import { STYLES } from "../src/content/styles";

describe("folded row animation", () => {
  it("animates the grid row height with the iOS-like curve, 220 ms open and 180 ms close", () => {
    expect(STYLES).toContain("--fold-ease: cubic-bezier(0.32, 0.72, 0, 1)");
    expect(STYLES).toContain(".fold-body { display: grid; grid-template-rows: 0fr; transition: grid-template-rows 180ms");
    expect(STYLES).toContain(".fold.open .fold-body { grid-template-rows: 1fr; transition-duration: 220ms; }");
  });

  it("turns the animation off for reduced motion", () => {
    expect(STYLES).toMatch(/@media \(prefers-reduced-motion: reduce\) \{\s*\.fold-button svg, \.fold-body, \.fold-inner \{ transition: none !important; \}/);
  });
});
