/** The toolbar popup: settings now; the account section comes with sign-in. */
import { mountSettings } from "./settingsView";

void mountSettings(document, chrome.storage.sync);

// Show the shortcut the user actually set (brave://extensions/shortcuts can change it).
void chrome.commands.getAll().then((commands) => {
  const shortcut = commands.find((command) => command.name === "explain-selection")?.shortcut;
  const element = document.getElementById("shortcut");
  if (element && shortcut) element.textContent = shortcut;
});
