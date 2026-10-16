/**
 * The toolbar popup: the account and the settings. The same page opened as a tab (`?tab=1`) holds the sign-in
 * form, because the popup closes when it loses focus (accountView.ts).
 */
import { SESSION_KEY, type Session } from "../shared/auth";
import type { RuntimeRequest, RuntimeResponse } from "../shared/messages";
import { mountAccount, PENDING_KEY, type PendingSignIn } from "./accountView";
import { mountSettings } from "./settingsView";

const isPage = new URLSearchParams(location.search).has("tab");
if (isPage) document.body.classList.add("page");

void mountAccount(document, {
  mode: isPage ? "page" : "popup",
  send: (request: RuntimeRequest) => chrome.runtime.sendMessage<RuntimeRequest, RuntimeResponse>(request),
  session: async () => ((await chrome.storage.local.get(SESSION_KEY))[SESSION_KEY] as Session | undefined) ?? null,
  onSessionChange: (listener) =>
    chrome.storage.onChanged.addListener((changes, area) => {
      if (area === "local" && SESSION_KEY in changes) listener();
    }),
  pending: {
    get: async () => ((await chrome.storage.local.get(PENDING_KEY))[PENDING_KEY] as PendingSignIn | undefined) ?? null,
    set: (value) => chrome.storage.local.set({ [PENDING_KEY]: value }),
    clear: () => chrome.storage.local.remove(PENDING_KEY),
  },
  openSignInPage: () => {
    void chrome.runtime.sendMessage<RuntimeRequest>({ type: "openSignIn" });
    window.close();
  },
  // The page goes away after a right code; a card that asked for sign-in runs its text again.
  signedIn: isPage ? () => setTimeout(() => window.close(), 800) : undefined,
});
void mountSettings(document, chrome.storage.sync);

// Show the shortcut the user actually set (brave://extensions/shortcuts can change it).
void chrome.commands.getAll().then((commands) => {
  const shortcut = commands.find((command) => command.name === "explain-selection")?.shortcut;
  const element = document.getElementById("shortcut");
  if (element && shortcut) element.textContent = shortcut;
});
