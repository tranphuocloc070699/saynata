/**
 * The account part of the popup, the same steps as the approved iOS sign-in sheet (option A, doc-7 10-15):
 * 1. email and a round icon-only send button; 2. the 6-digit code (checked as soon as it has 6 digits) with a
 * "Send a new code" link; signed in: the email and an icon-only Sign out button.
 * The popup never changes the session itself: it asks the service worker (RuntimeRequest).
 */
import type { Session } from "../shared/auth";
import type { RuntimeRequest, RuntimeResponse } from "../shared/messages";

export interface AccountDeps {
  send(request: RuntimeRequest): Promise<RuntimeResponse>;
  session(): Promise<Session | null>;
  /** Calls back when the session changes (signed in or out somewhere else). */
  onSessionChange(listener: () => void): void;
  /** After a right code; the popup opened as a tab closes itself. */
  signedIn?(): void;
}

const ICONS = {
  send: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>',
  signOut: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/></svg>',
};

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Step = { name: "email"; email: string } | { name: "code"; email: string } | { name: "signedIn"; email: string };

export async function mountAccount(root: Document, deps: AccountDeps): Promise<void> {
  const section = root.getElementById("account") as HTMLElement;
  section.hidden = false;
  let step: Step = await currentStep({ name: "email", email: "" });
  let error = "";
  let busy = false;

  async function currentStep(fallback: Step): Promise<Step> {
    const session = await deps.session();
    if (session) return { name: "signedIn", email: session.user.email };
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

  function iconButton(icon: string, label: string, prominent: boolean): HTMLButtonElement {
    const button = el("button", { type: "submit", class: prominent ? "round prominent" : "round", "aria-label": label, title: label });
    button.innerHTML = icon;
    return button;
  }

  function errorLine(): HTMLElement | null {
    return error ? el("p", { class: "error", role: "alert" }, error) : null;
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
    if (response.ok) step = { name: "code", email };
    render();
  }

  async function verify(code: string): Promise<void> {
    const response = await run({ type: "auth/verify", email: step.email, code });
    if (response.ok) {
      step = { name: "signedIn", email: response.email ?? step.email };
      deps.signedIn?.();
    }
    render();
  }

  function render(): void {
    const heading = el("h2", { class: "heading" }, "Account");
    const card = el("div", { class: "card account" });
    if (step.name === "signedIn") {
      const row = el("form", { class: "row" });
      row.append(el("span", { class: "who" }, step.email), iconButton(ICONS.signOut, "Sign out", false));
      row.addEventListener("submit", (event) => {
        event.preventDefault();
        void run({ type: "auth/signOut" }).then(() => {
          step = { name: "email", email: "" };
          render();
        });
      });
      card.append(row);
      section.replaceChildren(heading, card, ...[errorLine()].filter((n): n is HTMLElement => n !== null));
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
      section.replaceChildren(heading, card, ...[errorLine()].filter((n): n is HTMLElement => n !== null));
      if (!busy) input.focus();
      return;
    }

    card.append(el("p", { class: "lead" }, `Enter the 6-digit code we sent to ${step.email}.`));
    const input = el("input", {
      id: "code",
      class: "code",
      inputmode: "numeric",
      autocomplete: "one-time-code",
      maxlength: "6",
      "aria-label": "6-digit code",
    });
    input.addEventListener("input", () => {
      input.value = input.value.replace(/\D/g, "").slice(0, 6);
      if (input.value.length === 6 && !busy) void verify(input.value);
    });
    const links = el("div", { class: "links" });
    const resend = el("button", { type: "button", class: "link" }, "Send a new code");
    resend.addEventListener("click", () => void sendCode(step.email));
    const other = el("button", { type: "button", class: "link" }, "Use another email");
    other.addEventListener("click", () => {
      step = { name: "email", email: step.email };
      error = "";
      render();
    });
    links.append(resend, other);
    card.append(input, links);
    section.replaceChildren(heading, card, ...[errorLine()].filter((n): n is HTMLElement => n !== null));
    if (!busy) input.focus();
  }

  render();
}
