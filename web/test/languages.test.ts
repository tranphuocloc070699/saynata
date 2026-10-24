import { describe, expect, it } from "vitest";
import { LANGUAGES, languageFromSaved } from "../src/lib/languages";

describe("languages", () => {
  it("has no Thai", () => {
    expect(LANGUAGES.map((language) => language.code)).toEqual(["vi", "en", "ja", "ko", "zh", "fr", "es", "de"]);
  });

  it("reads a saved Thai choice (removed) as Vietnamese", () => {
    expect(languageFromSaved("th")).toBe("vi");
  });

  it("keeps a saved language that is still supported, and falls back on a missing value", () => {
    expect(languageFromSaved("ko")).toBe("ko");
    expect(languageFromSaved(null)).toBe("vi");
  });
});
