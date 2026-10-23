/**
 * The popover card: a state, a pure reducer for the service worker's events, and a render function.
 * Order follows the product rule (CLAUDE.md): the text, the simple English meaning, the words,
 * and the target-language row last, closed unless the Show translation setting says otherwise.
 */
import type { ApiErrorKind, Sense, TranslationResponse, WordResponse } from "../shared/api";
import { parseSummary } from "../shared/summary";
import { languageName, type LanguageCode } from "../shared/languages";
import type { ExplainEvent } from "../shared/messages";
import { wordQuery } from "../shared/textKind";
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
  /** A word opened from the sentence card's Words list; the sentence answer stays for Back. */
  opened?: OpenedWord;
}

export interface OpenedWord {
  word: string;
  response?: WordResponse;
  error?: { kind: ErrorKind; message: string };
}

export interface CardActions {
  close(): void;
  speak(text: string): void;
  copy(text: string): void;
  toggleRow(open: boolean): void;
  retry(): void;
  /** Missing until sign-in exists; then the guest card shows a sign-in button. */
  signIn?(): void;
  /** Opens one word of the sentence card's Words list as a word card. */
  openWord?(word: string): void;
  /** Back from an opened word to the sentence card. */
  back?(): void;
  /** The picture or clip frame for a sense's imageUrl (kept between renders), or null. */
  media?(url: string): HTMLElement | null;
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

/** Applies a service-worker event to the opened word (its own request). */
export function applyOpenedEvent(state: CardState, event: ExplainEvent): CardState {
  if (!state.opened) return state;
  if (event.type === "word") return { ...state, opened: { ...state.opened, response: event.response, error: undefined } };
  if (event.type === "error") return { ...state, opened: { ...state.opened, error: { kind: event.kind, message: event.message } } };
  return state;
}

/** The opened word drawn as its own word card, with the sentence card's language, row and speech state. */
function openedView(state: CardState, opened: OpenedWord): CardState {
  return {
    ...state,
    kind: "word",
    text: opened.word,
    word: opened.response,
    phase: opened.error ? "error" : opened.response ? "done" : "loading",
    error: opened.error,
    opened: undefined,
  };
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
  if (state.opened) return renderView(openedView(state, state.opened), actions, true);
  return renderView(state, actions, false);
}

function renderView(state: CardState, actions: CardActions, canGoBack: boolean): HTMLElement {
  const label = state.kind === "word" ? `Saynata: ${state.text}` : "Saynata explanation";
  const card = h("div", { class: "card", role: "dialog", "aria-label": label });
  card.append(header(state, actions, canGoBack));
  if (state.phase === "error" && state.error) {
    card.append(errorBlock(state.error, actions));
  } else if (state.kind === "sentence") {
    card.append(...sentenceBody(state, actions));
  } else {
    card.append(...wordBody(state, actions));
  }
  return card;
}

function header(state: CardState, actions: CardActions, canGoBack = false): HTMLElement {
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
    const back = canGoBack && actions.back ? iconButton(ICONS.back, "Back", () => actions.back?.()) : null;
    return h("div", { class: "head" }, back, title, h("div", { class: "actions" }, listen, close));
  }
  const copy = iconButton(state.copied ? ICONS.check : ICONS.copy, state.copied ? "Copied" : "Copy", () => actions.copy(state.text));
  const title = h("div", { class: "title" }, h("div", { class: "quote" }, `“${state.text}”`));
  return h("div", { class: "head" }, title, h("div", { class: "actions" }, listen, copy, close));
}

/**
 * The summary as DOM (TASK-89.3): plain lines as paragraphs, "- " lines as one list, **bold** as <b>.
 * While it grows, the caret sits at the end of the last line.
 */
function summaryNodes(markdown: string, growing: boolean): HTMLElement[] {
  const nodes: HTMLElement[] = [];
  let list: HTMLElement | null = null;
  let last: HTMLElement | null = null;
  for (const line of parseSummary(markdown)) {
    const inline = line.pieces.map((piece) => (piece.bold ? h("b", {}, piece.text) : piece.text));
    if (line.bullet) {
      if (!list) {
        list = h("ul", { class: "summary-list" });
        nodes.push(list);
      }
      last = h("li", {}, ...inline);
      list.append(last);
    } else {
      list = null;
      last = h("p", { class: "summary-line" }, ...inline);
      nodes.push(last);
    }
  }
  if (growing) {
    const caret = h("span", { class: "caret" });
    if (last) last.append(caret);
    else nodes.push(caret);
  }
  return nodes;
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
      : h("div", { class: "meaning", "aria-live": "polite" }, ...summaryNodes(state.meaning, growing));
  parts.push(h("div", { class: "block" }, h("div", { class: "label" }, "Meaning"), meaning));

  const words = state.sentence?.explanation.words ?? [];
  if (words.length > 0) {
    const rows = words.map((word) => {
      // Only a single word has a word card (GET /api/words/{word}); a phrase like "went ahead" has none.
      const single = wordQuery(word.word);
      const open =
        single && actions.openWord
          ? iconButton(ICONS.chevronRight, `Open ${single}`, () => actions.openWord?.(single), "small")
          : null;
      return h(
        "div",
        { class: "word-row" },
        h("span", { class: "word-text" }, h("b", {}, word.word), h("span", { class: "gloss" }, ` — ${word.meaning}`)),
        word.partOfSpeech ? h("span", { class: "chip" }, shortPos(word.partOfSpeech)) : null,
        open,
      );
    });
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
    const indent = senses.length > 1 ? "" : "padding-left: 14px";
    // Option A (doc-7 10-16): the picture or clip under the meaning, like the iOS word card.
    const media = sense.imageUrl ? actions.media?.(sense.imageUrl) : null;
    if (media) parts.push(h("div", { class: "media-row", style: indent }, media));
    const example = sense.examples?.[0]?.text;
    if (example) parts.push(h("div", { class: "example", style: indent }, example));
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

/**
 * A row that opens and closes, like the app's FoldedRow. The body is always in the DOM and the button toggles
 * the `open` class in place (no re-render), so CSS can animate the real height (styles.ts, "Folded rows").
 */
function fold(title: string, lines: string[], open: boolean, actions: CardActions): HTMLElement {
  const id = `saynata-fold-${++foldCount}`;
  const button = h(
    "button",
    { type: "button", class: "fold-button", "aria-expanded": String(open), "aria-controls": id },
    title,
    svgNode(ICONS.chevronDown),
  );
  const inner = h("div", { class: "fold-inner" }, h("div", { class: "fold-text" }, ...lines.map((line) => h("p", {}, line))));
  const body = h("div", { class: "fold-body", id, role: "region" }, inner);
  const row = h("div", { class: open ? "fold open" : "fold" }, button, body);
  // Closed content cannot be reached with Tab or read aloud.
  inner.inert = !open;
  button.addEventListener("click", (event) => {
    event.stopPropagation();
    const next = !row.classList.contains("open");
    row.classList.toggle("open", next);
    button.setAttribute("aria-expanded", String(next));
    inner.inert = !next;
    actions.toggleRow(next);
  });
  return row;
}

let foldCount = 0;

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
