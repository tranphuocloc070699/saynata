/**
 * Content script. TASK-86.1 only wires the messages: the right-click item and the shortcut explain the selection,
 * and the result is written to the page console. The icon and the card come in TASK-86.2.
 */
import { DEFAULT_LANGUAGE } from "../shared/languages";
import { EXPLAIN_PORT, type ExplainEvent, type TabMessage } from "../shared/messages";

chrome.runtime.onMessage.addListener((message: TabMessage) => {
  const text = message.type === "explainText" ? message.text : (window.getSelection()?.toString() ?? "");
  const port = chrome.runtime.connect({ name: EXPLAIN_PORT });
  port.onMessage.addListener((event: ExplainEvent) => {
    console.debug("Saynata", event);
    if (event.type === "word" || event.type === "sentence" || event.type === "error") port.disconnect();
  });
  port.postMessage({ text, targetLang: DEFAULT_LANGUAGE });
});
