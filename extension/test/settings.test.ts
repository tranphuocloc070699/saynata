import { describe, expect, it } from "vitest";
import { DEFAULT_SETTINGS, loadSettings, rowStartsOpen, saveSettings, type KeyValueArea } from "../src/shared/settings";

function area(initial: Record<string, unknown> = {}) {
  const data = { ...initial };
  const fake: KeyValueArea = {
    get: async (keys) => Object.fromEntries(keys.filter((key) => key in data).map((key) => [key, data[key]])),
    set: async (items) => void Object.assign(data, items),
  };
  return { fake, data };
}

describe("settings", () => {
  it("defaults: Vietnamese, collapsed", async () => {
    expect(await loadSettings(area().fake)).toEqual(DEFAULT_SETTINGS);
    expect(DEFAULT_SETTINGS).toMatchObject({ targetLang: "vi", showTranslation: "collapsed" });
  });

  it("reads saved values and ignores broken ones", async () => {
    const { fake } = area({ targetLang: "ko", showTranslation: "open", lastRowOpen: true });
    expect(await loadSettings(fake)).toEqual({ targetLang: "ko", showTranslation: "open", lastRowOpen: true });
    const broken = area({ targetLang: "xx", showTranslation: 3, lastRowOpen: "yes" });
    expect(await loadSettings(broken.fake)).toEqual(DEFAULT_SETTINGS);
  });

  it("reads a saved Thai choice (removed) as Vietnamese", async () => {
    const { fake } = area({ targetLang: "th" });
    expect((await loadSettings(fake)).targetLang).toBe("vi");
  });

  it("saves a change", async () => {
    const { fake, data } = area();
    await saveSettings(fake, { targetLang: "ja" });
    expect(data).toEqual({ targetLang: "ja" });
  });

  it("starts the row closed, open, or as last set", () => {
    expect(rowStartsOpen({ ...DEFAULT_SETTINGS, showTranslation: "collapsed", lastRowOpen: true })).toBe(false);
    expect(rowStartsOpen({ ...DEFAULT_SETTINGS, showTranslation: "open" })).toBe(true);
    expect(rowStartsOpen({ ...DEFAULT_SETTINGS, showTranslation: "remember", lastRowOpen: true })).toBe(true);
    expect(rowStartsOpen({ ...DEFAULT_SETTINGS, showTranslation: "remember", lastRowOpen: false })).toBe(false);
  });
});
