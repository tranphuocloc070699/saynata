import { describe, expect, it } from "vitest";
import { parseSummary } from "../src/shared/summary";

describe("parseSummary", () => {
  it("keeps a plain line", () => {
    expect(parseSummary("I do not know what to choose.")).toEqual([
      { bullet: false, pieces: [{ text: "I do not know what to choose.", bold: false }] },
    ]);
  });

  it("reads bullets and bold", () => {
    expect(parseSummary("Your **career** is at risk.\n- Use **private distribution**.")).toEqual([
      { bullet: false, pieces: [{ text: "Your ", bold: false }, { text: "career", bold: true }, { text: " is at risk.", bold: false }] },
      { bullet: true, pieces: [{ text: "Use ", bold: false }, { text: "private distribution", bold: true }, { text: ".", bold: false }] },
    ]);
  });

  it("makes the rest of a streaming line bold when the ** is not closed yet", () => {
    expect(parseSummary("A ban could hurt your **care")[0].pieces).toEqual([
      { text: "A ban could hurt your ", bold: false },
      { text: "care", bold: true },
    ]);
  });

  it("drops empty lines and Windows line ends", () => {
    expect(parseSummary("Gist.\r\n\r\n- One.").map((line) => line.bullet)).toEqual([false, true]);
  });
});
