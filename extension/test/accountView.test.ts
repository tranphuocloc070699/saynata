// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mountAccount, type AccountDeps } from "../src/popup/accountView";
import type { Session } from "../src/shared/auth";
import type { RuntimeRequest, RuntimeResponse } from "../src/shared/messages";

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

function deps(answers: Partial<Record<RuntimeRequest["type"], RuntimeResponse>> = {}, session: Session | null = null) {
  const sent: RuntimeRequest[] = [];
  const value: AccountDeps = {
    send: vi.fn(async (request: RuntimeRequest): Promise<RuntimeResponse> => {
      sent.push(request);
      return answers[request.type] ?? { ok: true as const };
    }),
    session: async () => session,
    onSessionChange: () => undefined,
  };
  return { value, sent };
}

beforeEach(() => {
  document.body.innerHTML = '<section id="account" hidden></section>';
});

const $ = <T extends Element>(selector: string) => document.querySelector(selector) as T;

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

describe("account popup", () => {
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

  it("sends the code, then signs in as soon as 6 digits are typed", async () => {
    const signedIn = vi.fn();
    const { value, sent } = deps({ "auth/verify": { ok: true, email: "lan@example.com" } });
    await mountAccount(document, { ...value, signedIn });
    await submitEmail("lan@example.com");
    expect($(".lead").textContent).toBe("Enter the 6-digit code we sent to lan@example.com.");
    await typeCode("12a34");
    expect(sent).toHaveLength(1);
    await typeCode("123456");
    expect(sent[1]).toEqual({ type: "auth/verify", email: "lan@example.com", code: "123456" });
    expect($(".who").textContent).toBe("lan@example.com");
    expect(signedIn).toHaveBeenCalled();
  });

  it("a wrong code shows the message and an empty field", async () => {
    const { value } = deps({ "auth/verify": { ok: false, kind: "invalidCode", message: "That code is wrong or has expired." } });
    await mountAccount(document, value);
    await submitEmail("lan@example.com");
    await typeCode("000000");
    expect($(".error").textContent).toBe("That code is wrong or has expired.");
    expect($<HTMLInputElement>("#code").value).toBe("");
  });

  it("Send a new code asks again for the same email", async () => {
    const { value, sent } = deps();
    await mountAccount(document, value);
    await submitEmail("lan@example.com");
    (Array.from(document.querySelectorAll("button.link")).find((b) => b.textContent === "Send a new code") as HTMLButtonElement).click();
    await flush();
    expect(sent).toEqual([
      { type: "auth/requestCode", email: "lan@example.com" },
      { type: "auth/requestCode", email: "lan@example.com" },
    ]);
  });

  it("signed in: the email and Sign out", async () => {
    const session = { accessToken: "a", refreshToken: "r", user: { id: 1, email: "lan@example.com" } };
    const { value, sent } = deps({}, session);
    await mountAccount(document, value);
    expect($(".who").textContent).toBe("lan@example.com");
    $<HTMLFormElement>("form").dispatchEvent(new Event("submit", { cancelable: true }));
    await flush();
    expect(sent).toEqual([{ type: "auth/signOut" }]);
  });
});
