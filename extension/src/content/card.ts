/**
 * The popover card: a state, a pure reducer for the service worker's events, and a render function.
 * Order follows the product rule (CLAUDE.md): the text, the simple English meaning, the words,
 * and the target-language row last, closed unless the Show translation setting says otherwise.
 */
import type { ApiErrorKind, Sense, TranslationResponse, WordResponse } from "../shared/api";
import { languageName, type LanguageCode } from "../shared/languages";
import type { ExplainEvent } from "../shared/messages";
import { ICONS } from "./icons";

export type ErrorKind = ApiErrorKind | "tooLong" | "empty";

export interface CardState {
  /** The selected text, cleaned. */
  text: string;
  kind: "word" | "sentence";
  phase: "loading" | "meaning" | "done" | "error";
  /** The meaning so far (sentence, while streaming), or the full one. */
  meaning: string;
  meaningFinal: boolean;
  sentence?: TranslationResponse;
  word?: WordResponse;
  error?: { kind: ErrorKind; message: string };
  targetLang: LanguageCode;
  rowOpen: boolean;
  speaking: boolean;
  copied: boolean;
}

export interface CardActions {
  close(): void;
  speak(text: string): void;
  copy(text: string): void;
  toggleRow(open: boolean): void;
  retry(): void;
  /** Missing until sign-in exists; then the guest card shows a sign-in button. */
  signIn?(): void;
}

export function initialState(
  text: string,
  kind: "word" | "sentence",
  targetLang: LanguageCode,
  rowOpen: boolean,
): CardState {
  return { text, kind, phase: "loading", meaning: "", meaningFinal: false, targetLang, rowOpen, speaking: false, copied: false };
}

/** Applies one service-worker event. A full `meaning` always wins over the pieces (doc-4). */
export function applyEvent(state: CardState, event: ExplainEvent): CardState {
  switch (event.type) {
    case "meaningDelta":
      if (state.meaningFinal) return state;
      return { ...state, phase: "meaning", meaning: state.meaning + event.text };
    case "meaning":
      return { ...state, phase: "meaning", meaning: event.meaning, meaningFinal: true };
    case "sentence":
      return {
        ...state,
        phase: "done",
        sentence: event.response,
        meaning: event.response.explanation.meaning,
        meaningFinal: true,
      };
    case "word":
      return { ...state, phase: "done", word: event.response };
    case "error":
      return { ...state, phase: "error", error: { kind: event.kind, message: event.message } };
  }
}

/** Short word type for the chip: "adjective" is "adj". */
export function shortPos(pos: string): string {
  const short: Record<string, string> = {
    adjective: "adj",
    adverb: "adv",
    preposition: "prep",
    conjunction: "conj",
    pronoun: "pron",
    determiner: "det",
    interjection: "interj",
    "phrasal verb": "phr v",
  };
  const key = pos.trim().toLowerCase();
  return short[key] ?? key;
}

// Rendering.

type Child = Node | string | null | undefined | false;

function h(tag: string, attrs: Record<string, string> = {}, ...children: Child[]): HTMLElement {
  const element = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs)) element.setAttribute(name, value);
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    element.append(child);
  }
  return element;
}

function iconButton(icon: string, label: string, onClick: () => void, extraClass = ""): HTMLElement {
  const button = h("button", { type: "button", class: `icon ${extraClass}`.trim(), "aria-label": label, title: label });
  button.innerHTML = icon;
  button.addEventListener("click", (event) => {
    event.stopPropagation();
    onClick();
  });
  return button;
}

function svgNode(icon: string): Node {
  const holder = document.createElement("span");
  holder.innerHTML = icon;
  return holder.firstElementChild ?? holder;
}

export function renderCard(state: CardState, actions: CardActions): HTMLElement {
  const label = state.kind === "word" ? `Saynata: ${state.text}` : "Saynata explanation";
  const card = h("div", { class: "card", role: "dialog", "aria-label": label });
  card.append(header(state, actions));
  if (state.phase === "error" && state.error) {
    card.append(errorBlock(state.error, actions));
  } else if (state.kind === "sentence") {
    card.append(...sentenceBody(state, actions));
  } else {
    card.append(...wordBody(state, actions));
  }
  return card;
}

function header(state: CardState, actions: CardActions): HTMLElement {
  const speakText = state.kind === "word" ? state.word?.word ?? state.text : state.text;
  const listen = iconButton(
    state.speaking ? ICONS.stop : ICONS.speaker,
    state.speaking ? "Stop" : "Listen",
    () => actions.speak(speakText),
    state.speaking ? "active" : "",
  );
  const close = iconButton(ICONS.close, "Close", () => actions.close());
  if (state.kind === "word") {
    const ipa = state.word?.ipa;
    const title = h("div", { class: "title" }, h("span", { class: "headword" }, state.word?.word ?? state.text), ipa ? h("span", { class: "ipa" }, ipa) : null);
    return h("div", { class: "head" }, title, h("div", { class: "actions" }, listen, close));
  }
  const copy = iconButton(state.copied ? ICONS.check : ICONS.copy, state.copied ? "Copied" : "Copy", () => actions.copy(state.text));
  const title = h("div", { class: "title" }, h("div", { class: "quote" }, `“${state.text}”`));
  return h("div", { class: "head" }, title, h("div", { class: "actions" }, listen, copy, close));
}

function skeleton(lines: number): HTMLElement[] {
  return Array.from({ length: lines }, (_, index) =>
    h("div", { class: "skeleton", style: `width: ${index === lines - 1 ? 60 : 100}%` }),
  );
}

function sentenceBody(state: CardState, actions: CardActions): HTMLElement[] {
  const parts: HTMLElement[] = [];
  const growing = state.phase === "meaning" && !state.meaningFinal;
  const meaning =
    state.phase === "loading"
      ? h("div", { "aria-busy": "true" }, ...skeleton(2))
      : h("div", { class: "meaning", "aria-live": "polite" }, state.meaning, growing ? h("span", { class: "caret" }) : null);
  parts.push(h("div", { class: "block" }, h("div", { class: "label" }, "Meaning"), meaning));

  const words = state.sentence?.explanation.words ?? [];
  if (words.length > 0) {
    const rows = words.map((word) =>
      h(
        "div",
        { class: "word-row" },
        h("span", {}, h("b", {}, word.word), h("span", { class: "gloss" }, ` — ${word.meaning}`)),
        word.partOfSpeech ? h("span", { class: "chip" }, shortPos(word.partOfSpeech)) : null,
      ),
    );
    parts.push(h("div", { class: "section" }, h("div", { class: "label", style: "margin-bottom: 4px" }, "Words"), ...rows));
  }

  // English has no target-language row (the app's rule).
  const translation = state.targetLang === "en" ? null : state.sentence?.explanation.translation;
  if (translation) parts.push(fold(languageName(state.targetLang), [translation], state.rowOpen, actions));
  return parts;
}

function wordBody(state: CardState, actions: CardActions): HTMLElement[] {
  if (!state.word) return [h("div", { class: "section", "aria-busy": "true" }, ...skeleton(3))];
  const senses = state.word.entry.senses ?? [];
  if (senses.length === 0) {
    return [messageBlock(null, "This doesn't look like an English word.", null, true)];
  }
  const parts: HTMLElement[] = [];
  senses.forEach((sense, index) => {
    parts.push(
      h(
        "div",
        { class: "sense" },
        senses.length > 1 ? h("span", { class: "num" }, String(index + 1)) : null,
        h("span", { class: "definition" }, sense.simpleDefinition),
        h("span", { class: "chip" }, chipText(sense)),
      ),
    );
    const example = sense.examples?.[0]?.text;
    if (example) parts.push(h("div", { class: "example", style: senses.length > 1 ? "" : "padding-left: 14px" }, example));
  });
  // The dictionary only has Vietnamese for a word; other languages have no row yet (doc-19 known gaps).
  if (state.targetLang === "vi") {
    const lines = senses
      .map((sense, index) => (senses.length > 1 ? `${index + 1}. ${sense.vietnamese}` : sense.vietnamese))
      .filter((line) => line.trim().length > 0);
    if (lines.length > 0) parts.push(fold(languageName("vi"), lines, state.rowOpen, actions));
  }
  return parts;
}

function chipText(sense: Sense): string {
  return [shortPos(sense.pos), sense.cefr].filter(Boolean).join(" · ");
}

function fold(title: string, lines: string[], open: boolean, actions: CardActions): HTMLElement {
  const button = h("button", { type: "button", class: "fold-button", "aria-expanded": String(open) }, title, svgNode(ICONS.chevronDown));
  button.addEventListener("click", (event) => {
    event.stopPropagation();
    actions.toggleRow(!open);
  });
  const body = open ? h("div", { class: "fold-body" }, ...lines.map((line) => h("p", {}, line))) : null;
  return h("div", { class: "fold" }, button, body);
}

function errorBlock(error: { kind: ErrorKind; message: string }, actions: CardActions): HTMLElement {
  if (error.kind === "signInRequired") {
    const button = actions.signIn ? iconButton(ICONS.signIn, "Sign in", () => actions.signIn?.(), "prominent") : null;
    return messageBlock(ICONS.lock, error.message, button, false);
  }
  const canRetry = error.kind === "network" || error.kind === "server" || error.kind === "streamEnded";
  const button = canRetry ? iconButton(ICONS.retry, "Try again", () => actions.retry()) : null;
  return messageBlock(null, error.message, button, true);
}

function messageBlock(icon: string | null, text: string, button: HTMLElement | null, isError: boolean): HTMLElement {
  return h(
    "div",
    { class: `block message${isError ? " error" : ""}`, role: isError ? "alert" : "status" },
    icon ? svgNode(icon) : null,
    h("span", { class: "text" }, text),
    button,
  );
}
