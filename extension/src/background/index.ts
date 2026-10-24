/**
 * The service worker: every network call happens here (host_permissions, so the backend needs no CORS rule),
 * plus the right-click menu item, the keyboard shortcut and the sign-in calls from the popup.
 */
import { ApiError, SaynataApi } from "../shared/api";
import { AuthClient, AuthError, AuthStore } from "../shared/auth";
import { API_BASE } from "../shared/config";
import { explain } from "../shared/explain";
import {
  EXPLAIN_PORT,
  type ExplainEvent,
  type ExplainRequest,
  type RuntimeRequest,
  type RuntimeResponse,
  type TabMessage,
} from "../shared/messages";

const MENU_ID = "saynata-explain";
const COMMAND = "explain-selection";

const auth = new AuthStore(chrome.storage.local, new AuthClient(API_BASE));
const api = new SaynataApi(API_BASE, auth);

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({ id: MENU_ID, title: "Explain with Saynata", contexts: ["selection"] });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId !== MENU_ID || !tab?.id || !info.selectionText) return;
  void sendToTab(tab.id, { type: "explainText", text: info.selectionText });
});

chrome.commands.onCommand.addListener((command, tab) => {
  if (command !== COMMAND || !tab?.id) return;
  void sendToTab(tab.id, { type: "explainSelection" });
});

chrome.runtime.onConnect.addListener((port) => {
  if (port.name !== EXPLAIN_PORT) return;
  const controller = new AbortController();
  let open = true;
  port.onDisconnect.addListener(() => {
    open = false;
    controller.abort();
  });
  port.onMessage.addListener((request: ExplainRequest) => {
    const emit = (event: ExplainEvent) => {
      if (open) port.postMessage(event);
    };
    void explain(api, request, emit, controller.signal);
  });
});

chrome.runtime.onMessage.addListener((request: RuntimeRequest, sender, sendResponse) => {
  // Only the extension's own pages and content scripts send these; a web page cannot reach onMessage.
  if (sender.id !== chrome.runtime.id) return false;
  void handle(request).then(sendResponse);
  return true; // the answer comes later
});

async function handle(request: RuntimeRequest): Promise<RuntimeResponse> {
  try {
    switch (request.type) {
      case "auth/requestCode":
        await auth.requestCode(request.email);
        return { ok: true };
      case "auth/verify":
        return { ok: true, email: (await auth.signIn(request.email, request.code)).email };
      case "auth/signOut":
        await auth.signOut();
        return { ok: true };
      case "openSignIn":
        await openSignIn();
        return { ok: true };
      case "lines":
        return { ok: true, ...(await api.translateLines(request.texts, request.targetLang)) };
    }
  } catch (error) {
    if (error instanceof ApiError) return { ok: false, kind: error.kind, message: error.message };
    if (error instanceof AuthError) return { ok: false, kind: error.kind, message: error.message };
    return { ok: false, kind: "server", message: "Something went wrong. Try again." };
  }
}

/**
 * The sign-in form opens in a tab, not the toolbar popup: the popup closes as soon as it loses focus, for
 * example when the user switches to their mail to read the code. An open sign-in tab is reused.
 */
async function openSignIn(): Promise<void> {
  const url = chrome.runtime.getURL("popup/index.html?tab=1");
  // Without the "tabs" permission the query may find nothing; then a new tab opens, which is fine.
  const [open] = await chrome.tabs.query({ url }).catch(() => []);
  if (open?.id !== undefined) {
    await chrome.tabs.update(open.id, { active: true });
    if (open.windowId !== undefined) await chrome.windows.update(open.windowId, { focused: true });
    return;
  }
  await chrome.tabs.create({ url });
}

/**
 * Tabs that were open before the extension was installed have no content script yet. The menu click and the
 * shortcut grant activeTab, so the script can be added to that tab and the message sent again.
 */
async function sendToTab(tabId: number, message: TabMessage): Promise<void> {
  try {
    await chrome.tabs.sendMessage(tabId, message);
  } catch {
    try {
      await chrome.scripting.executeScript({ target: { tabId }, files: ["content.js"] });
      await chrome.tabs.sendMessage(tabId, message);
    } catch {
      // Pages like chrome:// and the Web Store cannot run extension scripts.
    }
  }
}
