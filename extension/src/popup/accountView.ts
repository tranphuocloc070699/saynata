/**
 * The account part of the popup page, the same steps as the approved iOS sign-in sheet (option A, doc-7 10-15):
 * 1. email and a round icon-only send button; 2. the 6-digit code in six boxes (checked as soon as it has
 * 6 digits) with "Send a new code"; signed in: the email and an icon-only Sign out button.
 *
 * The toolbar popup closes as soon as it loses focus (for example when the user opens their mail to read the
 * code), so the form itself only lives in the same page opened as a tab ("page" mode). The popup shows a
 * round Sign in button that opens that tab. The step in progress (email, code sent at) is saved, so a page
 * opened again goes straight to the code boxes for that email.
 * The page never changes the session itself: it asks the service worker (RuntimeRequest).
 */
import type { Session } from "../shared/auth";
import type { RuntimeRequest, RuntimeResponse } from "../shared/messages";

/** A code was sent to this email at this time. The server keeps a code for 10 minutes (doc-17). */
export interface PendingSignIn {
  email: string;
  sentAt: number;
}

export const PENDING_KEY = "signInPending";
export const CODE_LIFETIME_MS = 10 * 60 * 1000;

export interface AccountDeps {
  /** "popup": the toolbar popup (a Sign in button opens the page); "page": the full sign-in form in a tab. */
  mode: "popup" | "page";
  send(request: RuntimeRequest): Promise<RuntimeResponse>;
  session(): Promise<Session | null>;
  /** Calls back when the session changes (signed in or out somewhere else). */
  onSessionChange(listener: () => void): void;
  pending: {
    get(): Promise<PendingSignIn | null>;
    set(value: PendingSignIn): Promise<void>;
    clear(): Promise<void>;
  };
  /** Popup mode: opens the sign-in page in a tab. */
  openSignInPage?(): void;
  /** After a right code; the page closes itself. */
  signedIn?(): void;
  now?(): number;
}

const ICONS = {
  send: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>',
  signIn: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><path d="m10 17 5-5-5-5"/><path d="M15 12H3"/></svg>',
  signOut: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/></svg>',
};

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * The code from whatever was typed, pasted or autofilled: "Code: 123456" and "123 456" give 123456.
 * When the text has a run of exactly 6 digits, that run wins, so "Your code 123456 ends in 10 minutes" works.
 */
export function extractCode(text: string): string {
  const run = text.match(/(?<!\d)\d{6}(?!\d)/);
  return run ? run[0] : text.replace(/\D/g, "").slice(0, 6);
}

/** A saved step is used only while its code can still work. */
export function isFresh(pending: PendingSignIn | null, now: number): pending is PendingSignIn {
  return pending !== null && EMAIL.test(pending.email) && now - pending.sentAt < CODE_LIFETIME_MS;
}

type Step = { name: "email"; email: string } | { name: "code"; email: string } | { name: "signedIn"; email: string };

export async function mountAccount(root: Document, deps: AccountDeps): Promise<void> {
  const section = root.getElementById("account") as HTMLElement;
  section.hidden = false;
  const now = deps.now ?? (() => Date.now());
  let step: Step = await currentStep({ name: "email", email: "" });
  let error = "";
  let busy = false;

  async function currentStep(fallback: Step): Promise<Step> {
    const session = await deps.session();
    if (session) return { name: "signedIn", email: session.user.email };
    if (fallback.name !== "email" || fallback.email === "") {
      const pending = await deps.pending.get();
      if (isFresh(pending, now())) return { name: "code", email: pending.email };
    }
    return fallback.name === "signedIn" ? { name: "email", email: "" } : fallback;
  }

  deps.onSessionChange(() => {
    void currentStep(step).then((next) => {
      step = next;
      render();
    });
  });

  function el<K extends keyof HTMLElementTagNameMap>(tag: K, attrs: Record<string, string> = {}, text?: string) {
    const element = root.createElement(tag);
    for (const [name, value] of Object.entries(attrs)) element.setAttribute(name, value);
    if (text !== undefined) element.textContent = text;
    return element;
  }

  function iconButton(icon: string, label: string, prominent: boolean, type: "submit" | "button" = "submit"): HTMLButtonElement {
    const button = el("button", { type, class: prominent ? "round prominent" : "round", "aria-label": label, title: label });
    button.innerHTML = icon;
    return button;
  }

  function show(...nodes: (HTMLElement | null)[]): void {
    const error_ = error ? el("p", { class: "error", role: "alert" }, error) : null;
    section.replaceChildren(el("h2", { class: "heading" }, "Account"), ...[...nodes, error_].filter((n): n is HTMLElement => n !== null));
  }

  async function run(request: RuntimeRequest): Promise<RuntimeResponse> {
    busy = true;
    error = "";
    render();
    const response = await deps.send(request).catch(
      (): RuntimeResponse => ({ ok: false, kind: "network", message: "Can't reach Saynata. Check your connection and try again." }),
    );
    busy = false;
    if (!response.ok) error = response.message;
    return response;
  }

  async function sendCode(email: string): Promise<void> {
    if (!EMAIL.test(email)) {
      error = "Enter a valid email address.";
      render();
      return;
    }
    const response = await run({ type: "auth/requestCode", email });
    if (response.ok) {
      step = { name: "code", email };
      await deps.pending.set({ email, sentAt: now() });
    }
    render();
  }

  async function verify(code: string): Promise<void> {
    const response = await run({ type: "auth/verify", email: step.email, code });
    if (response.ok) {
      step = { name: "signedIn", email: response.email ?? step.email };
      await deps.pending.clear();
      deps.signedIn?.();
    }
    render();
  }

  function render(): void {
    const card = el("div", { class: "card account" });

    if (step.name === "signedIn") {
      const row = el("form", { class: "row" });
      row.append(el("span", { class: "who" }, step.email), iconButton(ICONS.signOut, "Sign out", false));
      row.addEventListener("submit", (event) => {
        event.preventDefault();
        void run({ type: "auth/signOut" }).then(async () => {
          await deps.pending.clear();
          step = { name: "email", email: "" };
          render();
        });
      });
      card.append(row);
      show(card);
      return;
    }

    if (deps.mode === "popup") {
      // The popup closes when it loses focus, so the form lives in a tab that stays open while the mail is read.
      const row = el("div", { class: "row" });
      const lead =
        step.name === "code"
          ? `A code was sent to ${step.email}. Open sign-in to enter it.`
          : "Sign in to get new explanations. Saved answers work without an account.";
      const button = iconButton(ICONS.signIn, "Sign in", true, "button");
      button.addEventListener("click", () => deps.openSignInPage?.());
      row.append(el("p", { class: "lead inline" }, lead), button);
      card.append(row);
      show(card);
      return;
    }

    if (step.name === "email") {
      card.append(el("p", { class: "lead" }, "Sign in to get new explanations. Saved answers work without an account."));
      const form = el("form", { class: "row" });
      const input = el("input", {
        type: "email",
        id: "email",
        autocomplete: "email",
        placeholder: "you@example.com",
        "aria-label": "Email",
        required: "",
      });
      input.value = step.email;
      const button = iconButton(ICONS.send, "Send code", true);
      if (busy) button.setAttribute("aria-busy", "true");
      form.append(input, button);
      form.addEventListener("submit", (event) => {
        event.preventDefault();
        if (!busy) void sendCode(input.value.trim());
      });
      card.append(form);
      show(card);
      if (!busy) input.focus();
      return;
    }

    card.append(el("p", { class: "lead" }, `Enter the 6-digit code we sent to ${step.email}.`));
    card.append(codeBoxes());
    const links = el("div", { class: "links" });
    const resend = el("button", { type: "button", class: "link" }, "Send a new code");
    resend.addEventListener("click", () => void sendCode(step.email));
    const other = el("button", { type: "button", class: "link" }, "Use another email");
    other.addEventListener("click", () => {
      step = { name: "email", email: step.email };
      error = "";
      void deps.pending.clear();
      render();
    });
    links.append(resend, other);
    card.append(links);
    show(card);
    if (!busy) (root.getElementById("code") as HTMLInputElement).focus();
  }

  /**
   * Six boxes over one real input, like the iOS `.oneTimeCode` field: one field means paste and the browser's
   * one-time-code autofill fill all six at once. Only digits are kept.
   */
  function codeBoxes(): HTMLElement {
    const wrap = el("div", { class: "code-wrap" });
    const boxes = Array.from({ length: 6 }, () => el("span", { class: "box", "aria-hidden": "true" }));
    const input = el("input", {
      id: "code",
      class: "code-input",
      inputmode: "numeric",
      autocomplete: "one-time-code",
      "aria-label": "6-digit code",
    });
    const paint = () => {
      const focused = root.activeElement === input;
      boxes.forEach((box, index) => {
        box.textContent = input.value[index] ?? "";
        box.classList.toggle("current", focused && index === Math.min(input.value.length, 5));
      });
    };
    const accept = (text: string) => {
      input.value = extractCode(text);
      paint();
      if (input.value.length === 6 && !busy) void verify(input.value);
    };
    // maxlength would cut "Code: 123456" to "Code: ", so a paste is read before the field changes.
    input.addEventListener("paste", (event) => {
      event.preventDefault();
      accept(event.clipboardData?.getData("text") ?? "");
    });
    input.addEventListener("input", () => accept(input.value));
    input.addEventListener("focus", paint);
    input.addEventListener("blur", paint);
    wrap.append(...boxes, input);
    wrap.addEventListener("click", () => input.focus());
    return wrap;
  }

  render();
}
