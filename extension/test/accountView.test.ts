// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CODE_LIFETIME_MS, extractCode, isFresh, mountAccount, type AccountDeps, type PendingSignIn } from "../src/popup/accountView";
import type { Session } from "../src/shared/auth";
import type { RuntimeRequest, RuntimeResponse } from "../src/shared/messages";

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));
const NOW = 1_800_000_000_000;

function deps(
  options: {
    answers?: Partial<Record<RuntimeRequest["type"], RuntimeResponse>>;
    session?: Session | null;
    pending?: PendingSignIn | null;
    mode?: "popup" | "page";
  } = {},
) {
  const sent: RuntimeRequest[] = [];
  const store = { pending: options.pending ?? null };
  const value: AccountDeps = {
    mode: options.mode ?? "page",
    send: vi.fn(async (request: RuntimeRequest): Promise<RuntimeResponse> => {
      sent.push(request);
      return options.answers?.[request.type] ?? { ok: true as const };
    }),
    session: async () => options.session ?? null,
    onSessionChange: () => undefined,
    pending: {
      get: async () => store.pending,
      set: async (pending) => void (store.pending = pending),
      clear: async () => void (store.pending = null),
    },
    openSignInPage: vi.fn(),
    now: () => NOW,
  };
  return { value, sent, store };
}

beforeEach(() => {
  document.body.innerHTML = '<section id="account" hidden></section>';
});

const $ = <T extends Element>(selector: string) => document.querySelector(selector) as T;
const boxes = () => Array.from(document.querySelectorAll(".box")).map((box) => box.textContent).join("");

async function submitEmail(email: string) {
  $<HTMLInputElement>("#email").value = email;
  $<HTMLFormElement>("form").dispatchEvent(new Event("submit", { cancelable: true }));
  await flush();
}

async function typeCode(code: string) {
  const input = $<HTMLInputElement>("#code");
  input.value = code;
  input.dispatchEvent(new Event("input"));
  await flush();
}

async function pasteCode(text: string) {
  const event = new Event("paste", { cancelable: true }) as Event & { clipboardData: { getData(): string } };
  event.clipboardData = { getData: () => text };
  $<HTMLInputElement>("#code").dispatchEvent(event);
  await flush();
}

describe("extractCode", () => {
  it("keeps only digits, at most 6", () => {
    expect(extractCode("12a3 4")).toBe("1234");
    expect(extractCode("1234567")).toBe("123456");
  });

  it("finds the code in pasted text", () => {
    expect(extractCode("Code: 123456")).toBe("123456");
    expect(extractCode("Your Saynata code is 482913. It ends in 10 minutes.")).toBe("482913");
    expect(extractCode("123 456")).toBe("123456");
  });
});

describe("isFresh", () => {
  it("is true only while the code can still work", () => {
    expect(isFresh({ email: "lan@example.com", sentAt: NOW - 60_000 }, NOW)).toBe(true);
    expect(isFresh({ email: "lan@example.com", sentAt: NOW - CODE_LIFETIME_MS }, NOW)).toBe(false);
    expect(isFresh({ email: "broken", sentAt: NOW }, NOW)).toBe(false);
    expect(isFresh(null, NOW)).toBe(false);
  });
});

describe("account in the toolbar popup", () => {
  it("shows only a Sign in button, which opens the sign-in page", async () => {
    const { value } = deps({ mode: "popup" });
    await mountAccount(document, value);
    expect($("#email")).toBeNull();
    const button = $<HTMLButtonElement>("button.round");
    expect(button.getAttribute("aria-label")).toBe("Sign in");
    expect(button.textContent).toBe("");
    button.click();
    expect(value.openSignInPage).toHaveBeenCalled();
  });

  it("says a code is waiting when one was sent", async () => {
    const { value } = deps({ mode: "popup", pending: { email: "lan@example.com", sentAt: NOW - 1000 } });
    await mountAccount(document, value);
    expect($(".lead").textContent).toBe("A code was sent to lan@example.com. Open sign-in to enter it.");
  });
});

describe("sign-in page", () => {
  it("starts with the email and an icon-only send button", async () => {
    await mountAccount(document, deps().value);
    expect($("#account").hasAttribute("hidden")).toBe(false);
    const button = $<HTMLButtonElement>("button.round");
    expect(button.getAttribute("aria-label")).toBe("Send code");
    expect(button.textContent).toBe("");
  });

  it("checks the email before sending", async () => {
    const { value, sent } = deps();
    await mountAccount(document, value);
    await submitEmail("not-an-email");
    expect(sent).toEqual([]);
    expect($(".error").textContent).toBe("Enter a valid email address.");
  });

  it("saves the step when the code is sent", async () => {
    const { value, store } = deps();
    await mountAccount(document, value);
    await submitEmail("lan@example.com");
    expect(store.pending).toEqual({ email: "lan@example.com", sentAt: NOW });
    expect(document.querySelectorAll(".box")).toHaveLength(6);
  });

  it("opened again, goes straight to the code boxes for the saved email", async () => {
    const { value, sent } = deps({ pending: { email: "lan@example.com", sentAt: NOW - 120_000 } });
    await mountAccount(document, value);
    expect($(".lead").textContent).toBe("Enter the 6-digit code we sent to lan@example.com.");
    expect(sent).toEqual([]);
  });

  it("an old saved step is ignored", async () => {
    const { value } = deps({ pending: { email: "lan@example.com", sentAt: NOW - CODE_LIFETIME_MS - 1 } });
    await mountAccount(document, value);
    expect($("#email")).not.toBeNull();
  });

  it("signs in as soon as 6 digits are typed, and clears the saved step", async () => {
    const signedIn = vi.fn();
    const { value, sent, store } = deps({ answers: { "auth/verify": { ok: true, email: "lan@example.com" } } });
    await mountAccount(document, { ...value, signedIn });
    await submitEmail("lan@example.com");
    await typeCode("12a34");
    expect(boxes()).toBe("1234");
    expect(sent).toHaveLength(1);
    await typeCode("123456");
    expect(sent[1]).toEqual({ type: "auth/verify", email: "lan@example.com", code: "123456" });
    expect($(".who").textContent).toBe("lan@example.com");
    expect(signedIn).toHaveBeenCalled();
    expect(store.pending).toBeNull();
  });

  it('a pasted "Code: 123456" fills all six boxes and signs in', async () => {
    const { value, sent } = deps({ pending: { email: "lan@example.com", sentAt: NOW } });
    await mountAccount(document, value);
    await pasteCode("Code: 123456");
    expect(sent).toEqual([{ type: "auth/verify", email: "lan@example.com", code: "123456" }]);
  });

  it("a wrong code shows the message and empty boxes", async () => {
    const { value } = deps({
      pending: { email: "lan@example.com", sentAt: NOW },
      answers: { "auth/verify": { ok: false, kind: "invalidCode", message: "That code is wrong or has expired." } },
    });
    await mountAccount(document, value);
    await typeCode("000000");
    expect($(".error").textContent).toBe("That code is wrong or has expired.");
    expect(boxes()).toBe("");
  });

  it("Send a new code asks again for the same email", async () => {
    const { value, sent } = deps({ pending: { email: "lan@example.com", sentAt: NOW } });
    await mountAccount(document, value);
    (Array.from(document.querySelectorAll("button.link")).find((b) => b.textContent === "Send a new code") as HTMLButtonElement).click();
    await flush();
    expect(sent).toEqual([{ type: "auth/requestCode", email: "lan@example.com" }]);
  });

  it("Use another email forgets the saved step", async () => {
    const { value, store } = deps({ pending: { email: "lan@example.com", sentAt: NOW } });
    await mountAccount(document, value);
    (Array.from(document.querySelectorAll("button.link")).find((b) => b.textContent === "Use another email") as HTMLButtonElement).click();
    await flush();
    expect(store.pending).toBeNull();
    expect($<HTMLInputElement>("#email").value).toBe("lan@example.com");
  });

  it("signed in: the email and Sign out", async () => {
    const session = { accessToken: "a", refreshToken: "r", user: { id: 1, email: "lan@example.com" } };
    const { value, sent } = deps({ session });
    await mountAccount(document, value);
    expect($(".who").textContent).toBe("lan@example.com");
    $<HTMLFormElement>("form").dispatchEvent(new Event("submit", { cancelable: true }));
    await flush();
    expect(sent).toEqual([{ type: "auth/signOut" }]);
  });
});
