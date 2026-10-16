/** The toolbar popup: the account (sign in with an email code) and the settings. */
import { SESSION_KEY, type Session } from "../shared/auth";
import type { RuntimeRequest, RuntimeResponse } from "../shared/messages";
import { mountAccount } from "./accountView";
import { mountSettings } from "./settingsView";

const openedAsTab = new URLSearchParams(location.search).has("tab");

void mountAccount(document, {
  send: (request: RuntimeRequest) => chrome.runtime.sendMessage<RuntimeRequest, RuntimeResponse>(request),
  session: async () => ((await chrome.storage.local.get(SESSION_KEY))[SESSION_KEY] as Session | undefined) ?? null,
  onSessionChange: (listener) =>
    chrome.storage.onChanged.addListener((changes, area) => {
      if (area === "local" && SESSION_KEY in changes) listener();
    }),
  // Opened as a tab from a card (no popup there): go back to the page; the card runs the text again.
  signedIn: openedAsTab ? () => setTimeout(() => window.close(), 600) : undefined,
});
void mountSettings(document, chrome.storage.sync);

// Show the shortcut the user actually set (brave://extensions/shortcuts can change it).
void chrome.commands.getAll().then((commands) => {
  const shortcut = commands.find((command) => command.name === "explain-selection")?.shortcut;
  const element = document.getElementById("shortcut");
  if (element && shortcut) element.textContent = shortcut;
});
