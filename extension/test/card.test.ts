// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import { applyEvent, applyOpenedEvent, initialState, renderCard, shortPos, type CardActions, type CardState } from "../src/content/card";
import { sentenceResponse, wordResponse } from "./helpers";
import type { TranslationResponse, WordResponse } from "../src/shared/api";

function actions(): CardActions {
  return { close: vi.fn(), speak: vi.fn(), copy: vi.fn(), toggleRow: vi.fn(), retry: vi.fn() };
}

const sentenceDone = () =>
  applyEvent(initialState("reluctant to approve the plan", "sentence", "vi", false), {
    type: "sentence",
    response: sentenceResponse as TranslationResponse,
  });

const labels = (card: HTMLElement) => Array.from(card.querySelectorAll(".label, .fold-button")).map((e) => e.textContent);

describe("summary in the sentence card (TASK-89.3)", () => {
  it("shows the gist line, a list for bullets and bold words, never the markers", () => {
    let state = initialState("a long text", "sentence", "vi", false);
    state = applyEvent(state, { type: "meaning", meaning: "Your **career** is at risk.\n- Use <b>this</b>.\n- Leave." });
    const meaning = renderCard(state, actions()).querySelector(".meaning")!;

    expect(meaning.querySelector("p.summary-line")?.textContent).toBe("Your career is at risk.");
    expect(Array.from(meaning.querySelectorAll("li")).map((li) => li.textContent)).toEqual(["Use <b>this</b>.", "Leave."]);
    expect(Array.from(meaning.querySelectorAll("b")).map((b) => b.textContent)).toEqual(["career"]);
    expect(meaning.textContent).not.toContain("**");
  });

  it("puts the caret at the end of the last line while the summary grows", () => {
    let state = initialState("a long text", "sentence", "vi", false);
    state = applyEvent(state, { type: "meaningDelta", text: "Gist.\n- One **ide" });
    const meaning = renderCard(state, actions()).querySelector(".meaning")!;

    expect(meaning.querySelector("li .caret")).not.toBeNull();
    expect(meaning.querySelector("li b")?.textContent).toBe("ide");
  });
});

describe("applyEvent", () => {
  it("grows the meaning from deltas, and the full meaning wins", () => {
    let state = initialState("a b", "sentence", "vi", false);
    state = applyEvent(state, { type: "meaningDelta", text: "Not " });
    state = applyEvent(state, { type: "meaningDelta", text: "want" });
    expect(state).toMatchObject({ phase: "meaning", meaning: "Not want", meaningFinal: false });
    state = applyEvent(state, { type: "meaning", meaning: "Not wanting." });
    state = applyEvent(state, { type: "meaningDelta", text: " late" });
    expect(state).toMatchObject({ meaning: "Not wanting.", meaningFinal: true });
  });

  it("keeps the error", () => {
    const state = applyEvent(initialState("a b", "sentence", "vi", false), { type: "error", kind: "network", message: "x" });
    expect(state).toMatchObject({ phase: "error", error: { kind: "network", message: "x" } });
  });
});

describe("sentence card", () => {
  it("shows the text, then Meaning, then Words, then the target language last", () => {
    const card = renderCard(sentenceDone(), actions());
    expect(card.querySelector(".quote")?.textContent).toBe("“reluctant to approve the plan”");
    expect(labels(card)).toEqual(["Meaning", "Words", "Vietnamese"]);
    expect(card.querySelector(".meaning")?.textContent).toBe("Not wanting to say yes to the plan.");
    expect(card.querySelector(".word-row .chip")?.textContent).toBe("adj");
  });

  it("closes the target-language row by default and opens it when asked", () => {
    const closed = renderCard(sentenceDone(), actions());
    expect(closed.querySelector(".fold-button")?.getAttribute("aria-expanded")).toBe("false");
    expect(closed.querySelector(".fold")?.classList.contains("open")).toBe(false);
    const open = renderCard({ ...sentenceDone(), rowOpen: true }, actions());
    expect(open.querySelector(".fold-body")?.textContent).toBe("miễn cưỡng chấp thuận kế hoạch");
  });

  it("names the row after the picked language", () => {
    const state = { ...sentenceDone(), targetLang: "ko" as const };
    expect(renderCard(state, actions()).querySelector(".fold-button")?.textContent).toBe("Korean");
  });

  it("opening and closing the row toggles its class and aria-expanded in place, so CSS can animate it", () => {
    const a = actions();
    const card = renderCard(sentenceDone(), a);
    const row = card.querySelector(".fold") as HTMLElement;
    const button = row.querySelector(".fold-button") as HTMLButtonElement;
    const inner = row.querySelector(".fold-inner") as HTMLElement & { inert: boolean };
    expect(row.querySelector(".fold-text")?.textContent).toBe("miễn cưỡng chấp thuận kế hoạch");
    expect(button.getAttribute("aria-controls")).toBe(row.querySelector(".fold-body")?.id);
    expect(inner.inert).toBe(true);
    button.click();
    expect(row.classList.contains("open")).toBe(true);
    expect(button.getAttribute("aria-expanded")).toBe("true");
    expect(inner.inert).toBe(false);
    expect(card.querySelector(".fold")).toBe(row);
    button.click();
    expect(row.classList.contains("open")).toBe(false);
    expect(button.getAttribute("aria-expanded")).toBe("false");
    expect(a.toggleRow).toHaveBeenNthCalledWith(1, true);
    expect(a.toggleRow).toHaveBeenNthCalledWith(2, false);
  });

  it("a row rendered open starts with the class", () => {
    const row = renderCard({ ...sentenceDone(), rowOpen: true }, actions()).querySelector(".fold");
    expect(row?.classList.contains("open")).toBe(true);
    expect(row?.querySelector(".fold-button")?.getAttribute("aria-expanded")).toBe("true");
  });

  it("toggling the row calls the action", () => {
    const a = actions();
    (renderCard(sentenceDone(), a).querySelector(".fold-button") as HTMLButtonElement).click();
    expect(a.toggleRow).toHaveBeenCalledWith(true);
  });

  it("shows the growing meaning with a caret, and skeleton lines before it", () => {
    const loading = renderCard(initialState("a b", "sentence", "vi", false), actions());
    expect(loading.querySelectorAll(".skeleton").length).toBe(2);
    const growing = applyEvent(initialState("a b", "sentence", "vi", false), { type: "meaningDelta", text: "Not" });
    const card = renderCard(growing, actions());
    expect(card.querySelector(".meaning")?.textContent).toBe("Not");
    expect(card.querySelector(".caret")).not.toBeNull();
    expect(card.querySelector(".fold")).toBeNull();
  });

  it("buttons are icon-only, with labels", () => {
    const buttons = Array.from(renderCard(sentenceDone(), actions()).querySelectorAll("button.icon"));
    expect(buttons.map((b) => b.getAttribute("aria-label"))).toEqual(["Listen", "Copy", "Close"]);
    expect(buttons.every((b) => b.textContent === "")).toBe(true);
  });

  it("Listen reads the sentence", () => {
    const a = actions();
    (renderCard(sentenceDone(), a).querySelector('[aria-label="Listen"]') as HTMLButtonElement).click();
    expect(a.speak).toHaveBeenCalledWith("reluctant to approve the plan");
  });
});

describe("word card", () => {
  const wordDone = (lang: "vi" | "ja" = "vi") =>
    applyEvent(initialState("reluctant", "word", lang, false), { type: "word", response: wordResponse as WordResponse });

  it("shows the headword, IPA, meaning with its level, an example, then Vietnamese", () => {
    const card = renderCard(wordDone(), actions());
    expect(card.querySelector(".headword")?.textContent).toBe("reluctant");
    expect(card.querySelector(".ipa")?.textContent).toBe("/rɪˈlʌktənt/");
    expect(card.querySelector(".definition")?.textContent).toBe("Not wanting to do something.");
    expect(card.querySelector(".chip")?.textContent).toBe("adj · B2");
    expect(card.querySelector(".example")?.textContent).toBe("She was reluctant to leave.");
    expect(card.lastElementChild?.querySelector(".fold-button")?.textContent).toBe("Vietnamese");
  });

  it("has no target-language row for other languages yet", () => {
    expect(renderCard(wordDone("ja"), actions()).querySelector(".fold")).toBeNull();
  });

  it("says when the word is not English", () => {
    const empty = { ...wordResponse, entry: { senses: [] } } as WordResponse;
    const state = applyEvent(initialState("runk", "word", "vi", false), { type: "word", response: empty });
    expect(renderCard(state, actions()).textContent).toContain("This doesn't look like an English word.");
  });
});

describe("errors", () => {
  it("a guest sees the sign-in message, and the button once sign-in exists", () => {
    const state = applyEvent(initialState("a b", "sentence", "vi", false), {
      type: "error",
      kind: "signInRequired",
      message: "Sign in to get a new explanation.",
    });
    const without = renderCard(state, actions());
    expect(without.querySelector(".message")?.textContent).toBe("Sign in to get a new explanation.");
    expect(without.querySelector('[aria-label="Sign in"]')).toBeNull();
    const a = { ...actions(), signIn: vi.fn() };
    (renderCard(state, a).querySelector('[aria-label="Sign in"]') as HTMLButtonElement).click();
    expect(a.signIn).toHaveBeenCalled();
  });

  it("a network error can be tried again; a not-English answer cannot", () => {
    const network = applyEvent(initialState("a b", "sentence", "vi", false), { type: "error", kind: "network", message: "x" });
    expect(renderCard(network, actions()).querySelector('[aria-label="Try again"]')).not.toBeNull();
    const notEnglish = applyEvent(initialState("a b", "sentence", "vi", false), { type: "error", kind: "notEnglish", message: "x" });
    expect(renderCard(notEnglish, actions()).querySelector('[aria-label="Try again"]')).toBeNull();
  });
});

describe("shortPos", () => {
  it("shortens long word types", () => {
    expect(shortPos("Adjective")).toBe("adj");
    expect(shortPos("verb")).toBe("verb");
  });
});

describe("English as the target language", () => {
  it("has no target-language row, like the app", () => {
    const state = { ...sentenceDone(), targetLang: "en" as const };
    expect(renderCard(state, actions()).querySelector(".fold")).toBeNull();
  });
});

describe("open a word from the sentence card", () => {
  const sentenceWithWords = () =>
    applyEvent(initialState("they went ahead reluctantly", "sentence", "vi", false), {
      type: "sentence",
      response: {
        ...(sentenceResponse as TranslationResponse),
        explanation: {
          ...sentenceResponse.explanation,
          words: [
            { word: "reluctantly", partOfSpeech: "adverb", meaning: "not wanting to" },
            { word: "went ahead", partOfSpeech: "phrasal verb", meaning: "happened as planned" },
          ],
        },
      } as TranslationResponse,
    });

  it("each single word has an icon-only Open button; a phrase has none", () => {
    const a = { ...actions(), openWord: vi.fn() };
    const card = renderCard(sentenceWithWords(), a);
    const open = Array.from(card.querySelectorAll<HTMLButtonElement>(".word-row button"));
    expect(open.map((b) => b.getAttribute("aria-label"))).toEqual(["Open reluctantly"]);
    expect(open[0].textContent).toBe("");
    open[0].click();
    expect(a.openWord).toHaveBeenCalledWith("reluctantly");
  });

  it("the opened word shows a word card with Back, then its answer", () => {
    const a = { ...actions(), back: vi.fn() };
    let state: CardState = { ...sentenceDone(), opened: { word: "reluctant" } };
    const loading = renderCard(state, a);
    expect(loading.querySelector(".headword")?.textContent).toBe("reluctant");
    expect(loading.querySelectorAll(".skeleton").length).toBe(3);
    state = applyOpenedEvent(state, { type: "word", response: wordResponse as WordResponse });
    const card = renderCard(state, a);
    expect(card.querySelector(".definition")?.textContent).toBe("Not wanting to do something.");
    const back = card.querySelector<HTMLButtonElement>('[aria-label="Back"]');
    expect(back?.textContent).toBe("");
    back?.click();
    expect(a.back).toHaveBeenCalled();
  });

  it("keeps the sentence answer while a word is open", () => {
    const state = applyOpenedEvent({ ...sentenceDone(), opened: { word: "reluctant" } }, {
      type: "word",
      response: wordResponse as WordResponse,
    });
    expect(state.sentence?.explanation.meaning).toBe("Not wanting to say yes to the plan.");
    expect(renderCard({ ...state, opened: undefined }, actions()).querySelector(".meaning")?.textContent).toBe(
      "Not wanting to say yes to the plan.",
    );
  });

  it("a guest opening an uncached word sees the sign-in card inside the word view", () => {
    const state = applyOpenedEvent({ ...sentenceDone(), opened: { word: "zyzzyva" } }, {
      type: "error",
      kind: "signInRequired",
      message: "Sign in to get a new explanation.",
    });
    const card = renderCard(state, { ...actions(), back: vi.fn() });
    expect(card.querySelector(".message")?.textContent).toBe("Sign in to get a new explanation.");
    expect(card.querySelector('[aria-label="Back"]')).not.toBeNull();
  });
});

describe("pictures in the word card", () => {
  const withMedia = {
    ...wordResponse,
    entry: {
      senses: [
        { ...wordResponse.entry.senses[0], imageUrl: "https://api.saynata.com/media/reluctant_1.webp" },
        { ...wordResponse.entry.senses[0], simpleDefinition: "Second meaning." },
      ],
    },
  } as WordResponse;

  it("puts the picture under its meaning and before the example; a sense without one has none", () => {
    const frame = document.createElement("div");
    frame.className = "media";
    const media = vi.fn(() => frame);
    const state = applyEvent(initialState("reluctant", "word", "vi", false), { type: "word", response: withMedia });
    const card = renderCard(state, { ...actions(), media });
    expect(media).toHaveBeenCalledWith("https://api.saynata.com/media/reluctant_1.webp");
    expect(card.querySelectorAll(".media-row")).toHaveLength(1);
    const order = Array.from(card.children).map((e) => e.className.split(" ")[0]);
    expect(order).toEqual(["head", "sense", "media-row", "example", "sense", "example", "fold"]);
  });
});
