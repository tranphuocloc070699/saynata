// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { mountSettings } from "../src/popup/settingsView";
import type { KeyValueArea } from "../src/shared/settings";
import html from "../src/popup/index.html?raw";

function area(initial: Record<string, unknown> = {}) {
  const data: Record<string, unknown> = { ...initial };
  const fake: KeyValueArea = {
    get: async (keys) => Object.fromEntries(keys.filter((key) => key in data).map((key) => [key, data[key]])),
    set: async (items) => void Object.assign(data, items),
  };
  return { fake, data };
}

const body = html.slice(html.indexOf("<body>") + 6, html.indexOf("<script"));

beforeEach(() => {
  document.body.innerHTML = body;
});

const select = () => document.getElementById("language") as HTMLSelectElement;
const options = () => Array.from(document.querySelectorAll<HTMLButtonElement>("#modes .option"));
const checked = () => options().find((o) => o.getAttribute("aria-checked") === "true")?.textContent;

describe("settings popup", () => {
  it("lists every backend language and starts on the saved one", async () => {
    await mountSettings(document, area({ targetLang: "ko" }).fake);
    expect(Array.from(select().options).map((o) => o.value)).toEqual(["vi", "en", "ja", "ko", "zh", "th", "fr", "es", "de"]);
    expect(select().value).toBe("ko");
  });

  it("shows the three Show translation options, Collapsed by default", async () => {
    await mountSettings(document, area().fake);
    expect(options().map((o) => o.textContent)).toEqual(["Collapsed", "Always open", "Remember last choice"]);
    expect(checked()).toBe("Collapsed");
    expect(document.getElementById("modes-footer")?.textContent).toBe("How the Vietnamese row opens on words and sentences.");
  });

  it("saves a new mode and moves the checkmark", async () => {
    const { fake, data } = area();
    await mountSettings(document, fake);
    options()[2].click();
    expect(data.showTranslation).toBe("remember");
    expect(checked()).toBe("Remember last choice");
  });

  it("saves a new language and renames the footer", async () => {
    const { fake, data } = area();
    await mountSettings(document, fake);
    select().value = "ja";
    select().dispatchEvent(new Event("change"));
    expect(data.targetLang).toBe("ja");
    expect(document.getElementById("modes-footer")?.textContent).toBe("How the Japanese row opens on words and sentences.");
  });
});
