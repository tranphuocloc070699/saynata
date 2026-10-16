/**
 * The service worker: every network call happens here (host_permissions, so the backend needs no CORS rule),
 * plus the right-click menu item and the keyboard shortcut.
 */
import { SaynataApi } from "../shared/api";
import { API_BASE } from "../shared/config";
import { explain } from "../shared/explain";
import { EXPLAIN_PORT, type ExplainEvent, type ExplainRequest, type TabMessage } from "../shared/messages";

const MENU_ID = "saynata-explain";
const COMMAND = "explain-selection";

const api = new SaynataApi(API_BASE);

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
