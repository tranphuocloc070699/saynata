/** The settings part of the popup: Language and Show translation, like the iOS Settings screen. */
import { LANGUAGES, languageName, type LanguageCode } from "../shared/languages";
import {
  loadSettings,
  saveSettings,
  SHOW_TRANSLATION_MODES,
  type KeyValueArea,
  type Settings,
  type ShowTranslationMode,
} from "../shared/settings";

const CHECK =
  '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>';

export async function mountSettings(root: Document, area: KeyValueArea): Promise<void> {
  let settings: Settings = await loadSettings(area);
  const select = root.getElementById("language") as HTMLSelectElement;
  const modes = root.getElementById("modes") as HTMLElement;
  const footer = root.getElementById("modes-footer") as HTMLElement;

  for (const language of LANGUAGES) {
    const option = root.createElement("option");
    option.value = language.code;
    option.textContent = language.name;
    select.append(option);
  }
  select.value = settings.targetLang;
  select.addEventListener("change", () => {
    settings = { ...settings, targetLang: select.value as LanguageCode };
    void saveSettings(area, { targetLang: settings.targetLang });
    renderModes();
  });

  function renderModes(): void {
    modes.replaceChildren(
      ...SHOW_TRANSLATION_MODES.map(({ mode, label }) => {
        const button = root.createElement("button");
        button.type = "button";
        button.className = "option";
        button.setAttribute("role", "radio");
        button.setAttribute("aria-checked", String(settings.showTranslation === mode));
        button.dataset.mode = mode;
        button.innerHTML = `<span></span>${CHECK}`;
        (button.firstElementChild as HTMLElement).textContent = label;
        button.addEventListener("click", () => choose(mode));
        return button;
      }),
    );
    // English has no target-language row (same as the app's Settings footer).
    const name = settings.targetLang === "en" ? "translation" : languageName(settings.targetLang);
    footer.textContent = `How the ${name} row opens on words and sentences.`;
  }

  function choose(mode: ShowTranslationMode): void {
    settings = { ...settings, showTranslation: mode };
    void saveSettings(area, { showTranslation: mode });
    renderModes();
  }

  renderModes();
}
