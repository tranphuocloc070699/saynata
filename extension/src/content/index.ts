/**
 * Content script: shows the Saynata icon next to selected text, and the card when the icon, the right-click
 * item or the shortcut asks for it. Everything lives in a closed Shadow DOM, so page CSS and scripts cannot
 * reach it. Network calls happen in the service worker (EXPLAIN_PORT).
 */
import { SESSION_KEY } from "../shared/auth";
import { EXPLAIN_PORT, type ExplainEvent, type RuntimeRequest, type TabMessage } from "../shared/messages";
import { loadSettings, rowStartsOpen, saveSettings } from "../shared/settings";
import { classify, cleanSelection } from "../shared/textKind";
import { applyEvent, initialState, renderCard, type CardActions, type CardState } from "./card";
import { cardTop, iconPosition, placeCard, type CardPlacement, type Rect, type Viewport } from "./placement";
import { Speech } from "./speech";
import { STYLES } from "./styles";

declare global {
  interface Window {
    __saynataContent?: boolean;
  }
}

class SelectionPopover {
  private readonly host = document.createElement("saynata-popover");
  private readonly layer: HTMLDivElement;
  private trigger: HTMLButtonElement | null = null;
  private card: HTMLElement | null = null;
  private state: CardState | null = null;
  private port: chrome.runtime.Port | null = null;
  /** Where the card sits; chosen once when it opens (placeCard), so it never jumps. */
  private placement: CardPlacement | null = null;
  /** The card's top once the answer is complete; fixed from then on (cardTop). */
  private frozenTop: number | null = null;
  private selectionRect: Rect | null = null;
  private pendingText = "";
  private readonly speech = new Speech((speaking) => this.update({ speaking }));

  constructor() {
    const shadow = this.host.attachShadow({ mode: "closed" });
    const style = document.createElement("style");
    style.textContent = STYLES;
    this.layer = document.createElement("div");
    this.layer.className = "layer";
    shadow.append(style, this.layer);
    document.documentElement.append(this.host);

    document.addEventListener("mouseup", (event) => this.onMouseUp(event));
    document.addEventListener("keyup", (event) => this.onKeyUp(event));
    document.addEventListener("mousedown", (event) => this.onMouseDown(event), true);
    document.addEventListener("keydown", (event) => this.onKeyDown(event), true);
    document.addEventListener("selectionchange", () => this.onSelectionChange());
    chrome.runtime.onMessage.addListener((message: TabMessage) => this.onTabMessage(message));
    chrome.storage.onChanged.addListener((changes, area) => {
      if (area === "local" && changes[SESSION_KEY]?.newValue) this.onSignedIn();
    });
  }

  // Page events.

  private isInside(event: Event): boolean {
    return event.composedPath().includes(this.host);
  }

  private onMouseUp(event: MouseEvent): void {
    if (this.isInside(event) || event.button === 2) return;
    // The selection is final only after the click finishes.
    setTimeout(() => this.showTriggerForSelection(), 0);
  }

  private onKeyUp(event: KeyboardEvent): void {
    if (event.shiftKey || event.key === "Shift") setTimeout(() => this.showTriggerForSelection(), 0);
  }

  private onMouseDown(event: MouseEvent): void {
    if (this.isInside(event)) return;
    this.hideTrigger();
    if (this.card) this.closeCard();
  }

  private onKeyDown(event: KeyboardEvent): void {
    if (event.key !== "Escape") return;
    if (this.card) {
      this.closeCard();
      event.stopPropagation();
    } else {
      this.hideTrigger();
    }
  }

  private onSelectionChange(): void {
    if (this.trigger && currentSelection() === null) this.hideTrigger();
  }

  private onTabMessage(message: TabMessage): void {
    const selection = currentSelection();
    const text = message.type === "explainText" ? message.text : (selection?.text ?? "");
    const rect = selection?.rect ?? centreRect();
    void this.openCard(text, rect);
  }

  /** After a sign-in, a card that asked for one runs the same text again (like the iOS sheet). */
  private onSignedIn(): void {
    if (this.state?.error?.kind === "signInRequired") this.actions.retry();
  }

  // The icon.

  private showTriggerForSelection(): void {
    const selection = currentSelection();
    if (!selection || isEditing() || classify(selection.text).kind === "empty") {
      this.hideTrigger();
      return;
    }
    this.hideTrigger();
    this.pendingText = selection.text;
    const position = iconPosition(selection.lastLine, viewport());
    const button = document.createElement("button");
    button.type = "button";
    button.className = "trigger";
    button.textContent = "S";
    button.setAttribute("aria-label", "Explain with Saynata");
    button.title = "Explain with Saynata";
    button.style.left = `${position.left}px`;
    button.style.top = `${position.top}px`;
    // Keep the page selection: a mousedown on a button would clear it.
    button.addEventListener("mousedown", (event) => event.preventDefault());
    button.addEventListener("click", () => {
      const rect = currentSelection()?.rect ?? selection.rect;
      this.hideTrigger();
      void this.openCard(this.pendingText, rect);
    });
    this.layer.append(button);
    this.trigger = button;
  }

  private hideTrigger(): void {
    this.trigger?.remove();
    this.trigger = null;
  }

  // The card.

  private async openCard(rawText: string, rect: Rect): Promise<void> {
    this.closeCard();
    const text = cleanSelection(rawText);
    const kind = classify(text).kind === "word" ? "word" : "sentence";
    const settings = await loadSettings(chrome.storage.sync);
    this.selectionRect = rect;
    this.placement = null;
    this.frozenTop = null;
    this.state = initialState(text, kind, settings.targetLang, rowStartsOpen(settings));
    this.render();
    this.card?.focus({ preventScroll: true });
    this.request();
  }

  private request(): void {
    if (!this.state) return;
    this.port?.disconnect();
    const port = chrome.runtime.connect({ name: EXPLAIN_PORT });
    this.port = port;
    port.onMessage.addListener((event: ExplainEvent) => {
      if (this.port !== port || !this.state) return;
      this.state = applyEvent(this.state, event);
      this.render();
      if (event.type === "word" || event.type === "sentence" || event.type === "error") {
        port.disconnect();
        this.port = null;
      }
    });
    port.postMessage({ text: this.state.text, targetLang: this.state.targetLang });
  }

  private closeCard(): void {
    this.port?.disconnect();
    this.port = null;
    this.speech.stop();
    this.card?.remove();
    this.card = null;
    this.state = null;
    this.placement = null;
  }

  private update(change: Partial<CardState>): void {
    if (!this.state) return;
    this.state = { ...this.state, ...change };
    this.render();
  }

  private readonly actions: CardActions = {
    close: () => this.closeCard(),
    speak: (text) => this.speech.toggle(text),
    copy: (text) => {
      void navigator.clipboard.writeText(text).then(() => {
        this.update({ copied: true });
        setTimeout(() => this.update({ copied: false }), 1500);
      });
    },
    toggleRow: (open) => {
      this.update({ rowOpen: open });
      void saveSettings(chrome.storage.sync, { lastRowOpen: open });
    },
    signIn: () => {
      void chrome.runtime.sendMessage<RuntimeRequest>({ type: "openSignIn" });
    },
    retry: () => {
      if (!this.state) return;
      this.frozenTop = null;
      this.state = initialState(this.state.text, this.state.kind, this.state.targetLang, this.state.rowOpen);
      this.render();
      this.request();
    },
  };

  private render(): void {
    if (!this.state || !this.selectionRect) return;
    const next = renderCard(this.state, this.actions);
    next.tabIndex = -1;
    const scrollTop = this.card?.scrollTop ?? 0;
    const hadFocus = this.card?.matches(":focus-within") ?? false;
    if (this.card) this.card.replaceWith(next);
    else {
      next.classList.add("enter"); // the pop animation only when the card first opens
      this.layer.append(next);
    }
    this.card = next;
    next.scrollTop = scrollTop;
    if (hadFocus) next.focus({ preventScroll: true });

    if (!this.placement) {
      // Once, at open: the side, the left edge and the height limit. Later renders only follow the height.
      this.placement = placeCard(this.selectionRect, next.offsetWidth, viewport());
    }
    next.style.left = `${this.placement.left}px`;
    next.style.maxHeight = `${this.placement.maxHeight}px`;
    const top = cardTop(this.placement, next.offsetHeight, this.frozenTop);
    next.style.top = `${top}px`;
    if (this.state.phase === "done" || this.state.phase === "error") this.frozenTop = top;
  }
}

// Helpers.

function currentSelection(): { text: string; rect: Rect; lastLine: Rect } | null {
  const selection = window.getSelection();
  if (!selection || selection.isCollapsed || selection.rangeCount === 0) return null;
  const text = selection.toString();
  if (text.trim().length === 0) return null;
  const range = selection.getRangeAt(0);
  const rect = range.getBoundingClientRect();
  if (rect.width === 0 && rect.height === 0) return null;
  const lines = Array.from(range.getClientRects()).filter((line) => line.width > 0);
  return { text, rect, lastLine: lines[lines.length - 1] ?? rect };
}

/** No icon while the user types in a field: selecting text there is usually editing, not reading. */
function isEditing(): boolean {
  const active = document.activeElement as HTMLElement | null;
  if (!active) return false;
  return active.tagName === "INPUT" || active.tagName === "TEXTAREA" || active.isContentEditable;
}

function viewport(): Viewport {
  return {
    width: document.documentElement.clientWidth,
    height: window.innerHeight,
    scrollX: window.scrollX,
    scrollY: window.scrollY,
  };
}

/** Where the card goes when there is no selection on screen (the right-click text came from a frame). */
function centreRect(): Rect {
  const left = Math.max(8, document.documentElement.clientWidth / 2 - 170);
  return { left, right: left, top: 80, bottom: 80 };
}

if (!window.__saynataContent) {
  window.__saynataContent = true;
  new SelectionPopover();
}
