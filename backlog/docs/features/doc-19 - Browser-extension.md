---
id: doc-19
title: Browser extension
type: specification
created_date: '2026-10-16 09:35'
updated_date: '2026-10-16 09:47'
---
# Browser extension

> Status: **TASK-86.1, 86.2 and 86.4 built** (API client, stream, tests, CI; the selection icon and the card; the settings popup). Sign-in (TASK-86.3) comes next. Last traced from code: 2026-10-16.
> Plan: [doc-16](../doc-16%20-%20Plan-Mac-menu-bar-app-and-browser-extension.md) Part 2, option A. Related: [Sentence translation](doc-4%20-%20Sentence-translation.md) (the stream), [Word lookup](doc-5%20-%20Word-lookup.md), [Accounts and limits](doc-17%20-%20Accounts-and-limits.md).

## What it does

A Chromium Manifest V3 extension (Brave, Chrome, Edge, Arc) in `extension/`. The user selects English on any web page and gets the same answer as the app: one word goes to `GET /api/words/{word}`, more than one to `POST /api/translations/stream`, so the meaning grows word by word. It follows the product rule: simple English first, words next, the target language last and closed.

## Big picture

```mermaid
flowchart LR
    U[Selection on a page] --> CS[Content script]
    M[Right-click: Explain with Saynata] --> SW
    K[Shortcut Alt+Shift+S] --> SW
    SW[Service worker] -->|tabs.sendMessage| CS
    CS -->|port saynata-explain: text, targetLang| SW
    SW --> API[SaynataApi]
    API -->|GET /api/words/x| B[(api.loffy.me)]
    API -->|POST /api/translations/stream, fallback POST /api/translations| B
    SW -->|meaningDelta, meaning, word or sentence, error| CS
```

**Why the service worker makes every call:** an extension page or service worker with `host_permissions` for the API origin is not bound by CORS, so the backend needs no change (its CORS rule covers only `/api/admin/**`). A content script runs with the page's origin and would need CORS.

## Step by step

```mermaid
sequenceDiagram
    participant CS as Content script
    participant SW as Service worker
    participant API as api.loffy.me
    CS->>SW: connect port, post {text, targetLang}
    SW->>SW: classify(text): word, sentence, empty or too long
    alt one word
        SW->>API: GET /api/words/{word}
        API-->>SW: WordResponse
        SW-->>CS: {type: word}
    else sentence
        SW->>API: POST /api/translations/stream
        API-->>SW: meaning-delta..., meaning, done
        SW-->>CS: meaningDelta..., meaning, {type: sentence}
    end
    Note over CS,SW: closing the port aborts the fetch
```

| Step | File | What happens |
|---|---|---|
| Word or sentence | [textKind.ts:27](../../../extension/src/shared/textKind.ts#L27) | Collapses spaces, makes curly quotes straight. Over 500 characters is refused before any request. [wordQuery:36](../../../extension/src/shared/textKind.ts#L36): one token, or a/an/the plus one token (like the app, TASK-44), edge punctuation stripped, only letters, `'` and `-`, 1-64 characters (the word API's rule). |
| Word | [api.ts:167](../../../extension/src/shared/api.ts#L167) | `GET /api/words/{word}`. |
| Sentence | [api.ts:177](../../../extension/src/shared/api.ts#L177) | The stream first. If it fails with anything but a final 4xx answer or a cancel, `POST /api/translations` once (same as the iOS `explainWithFallback`). |
| Stream | [api.ts:204](../../../extension/src/shared/api.ts#L204), [sse.ts](../../../extension/src/shared/sse.ts) | Reads the body chunk by chunk through `SseParser` (lines may be cut anywhere, also inside a multi-byte character). `meaning-delta` gives new characters, `meaning` the full text (it wins), `done` the full `TranslationResponse`, `error` an `ApiError`. An end without `done` is `streamEnded` (then the fallback). |
| Bearer and retry | [api.ts:264](../../../extension/src/shared/api.ts#L264) | The token comes from a `TokenSource` (a guest has none). A 401 **with** a token refreshes once and runs the request again; the stream does the same for an `error` event with status 401 ([api.ts:274](../../../extension/src/shared/api.ts#L274)). A guest's 401 goes straight to the card. |
| Port | [background/index.ts:29](../../../extension/src/background/index.ts#L29), [explain.ts](../../../extension/src/shared/explain.ts) | One port per explanation; its events are typed in [messages.ts](../../../extension/src/shared/messages.ts). |
| Menu and shortcut | [background/index.ts:16](../../../extension/src/background/index.ts#L16), [:24](../../../extension/src/background/index.ts#L24) | "Explain with Saynata" on selected text; command `explain-selection` (default Alt+Shift+S, change it at `brave://extensions/shortcuts`). Tabs opened before the install get the content script on the first use ([sendToTab:49](../../../extension/src/background/index.ts#L49), through `activeTab` + `scripting`). |
| Icon | [content/index.ts:94](../../../extension/src/content/index.ts#L94), [placement.ts:30](../../../extension/src/content/placement.ts#L30) | After a mouse-up or a Shift selection, a round teal "S" (icon A, doc-7 10-16) appears just below the end of the selection's last line, inside the viewport. Not while typing in a field ([isEditing:238](../../../extension/src/content/index.ts#L238)). A mousedown on it keeps the page selection. It goes away on a click elsewhere, Escape, or an empty selection. |
| Card | [content/index.ts:129](../../../extension/src/content/index.ts#L129), [placement.ts:41](../../../extension/src/content/placement.ts#L41) | Opens under the selection at its left edge, above it when there is no room below; 340 px wide, at most 70% of the window high (scrolls inside). The settings (language, Show translation) are read when it opens. Closes on the Close button, Escape, or a click outside; closing disconnects the port, which aborts the request. Lives in a **closed Shadow DOM** (`<saynata-popover>`), so page CSS and scripts cannot reach it, and inline styles need no page CSP. |
| Card content | [card.ts:50](../../../extension/src/content/card.ts#L50), [renderCard:118](../../../extension/src/content/card.ts#L118) | `applyEvent` is a pure reducer: deltas add to the meaning (with a caret), the full `meaning` wins. Sentence: the quoted text with Listen, Copy and Close; Meaning; Words (word, gloss, word-type chip); the target-language row last. Word ([wordBody:184](../../../extension/src/content/card.ts#L184)): headword, IPA, Listen, Close; each sense with a number (when more than one), the simple definition, a `adj · B2` chip and the first example; then Vietnamese. Empty senses: "This doesn't look like an English word." |
| Target-language row | [fold:218](../../../extension/src/content/card.ts#L218), [settings.ts:47](../../../extension/src/shared/settings.ts#L47) | Titled with the language name, closed unless Show translation says otherwise (Collapsed default, Always open, Remember last choice: `lastRowOpen` is saved on every toggle, like iOS `TranslationRowPreference`). |
| Errors on the card | [errorBlock:228](../../../extension/src/content/card.ts#L228) | A guest sees a lock and "Sign in to get a new explanation." Network, server and cut streams get a Try again icon button; final answers (not English, 4xx) do not. |
| Settings popup | [popup/index.html](../../../extension/src/popup/index.html), [settingsView.ts:15](../../../extension/src/popup/settingsView.ts#L15) | The toolbar button opens it. **Language** (the 9 backend languages, default Vietnamese) and **Show translation** (Collapsed, Always open, Remember last choice, with a checkmark), the same as the iOS Settings screen. Saved at once in `chrome.storage.sync`; the next card uses them. English has no target-language row (the app's rule). The hint at the bottom shows the shortcut the user set. |
| Listen | [speech.ts](../../../extension/src/content/speech.ts) | `speechSynthesis`, `en-US`, a local US voice when there is one; the button turns into Stop while it reads. |
| Build | [scripts/build.mjs](../../../extension/scripts/build.mjs) | Vite builds each entry alone (a content script cannot import modules): `background.js` (ES module), `content.js` (IIFE), the popup, and writes `manifest.json`. `VITE_API_BASE=http://localhost:8081 npm run build` points it at a local backend (and asks host permission for that origin). |

## Errors

| Cause | Where | HTTP | What the user sees |
|---|---|---|---|
| Guest asks for a new sentence or an uncached word | `ClaudeGate` | 401 `sign_in_required` | "Sign in to get a new explanation." |
| Too many requests | `ClaudeGate`, per-IP filter | 429 `rate_limited` | "Too many requests. Try again in N minutes." (`Retry-After`, whole minutes; "in a little while" without it) |
| Today's budget used up | `ClaudeGate` | 503 `daily_limit_reached` | The server's message |
| Not English | Claude | 422 | The server's message |
| No network | fetch | - | "Can't reach Saynata. Check your connection and try again." |
| Over 500 characters / nothing selected | `classify` | - | "Select up to 500 characters." / "Select some English text first." |

## Tests

`cd extension && npm test` (Vitest). CI job "Browser extension": `npm ci`, `npm test`, `npm run build`.

| Test file | What it covers |
|---|---|
| [textKind.test.ts](../../../extension/test/textKind.test.ts) | Word vs sentence, articles, punctuation, `'` and `-`, digits, 64-character limit, empty, over 500, curly quotes |
| [sse.test.ts](../../../extension/test/sse.test.ts) | Events cut at every position, `\r\n`, a multi-byte character cut between chunks, comments, id/retry |
| [api.test.ts](../../../extension/test/api.test.ts) | Word URL, Bearer, refresh once then retry, only one retry, failed refresh, guest 401 with no refresh, network error; stream order, cache hit, coded error event, 422 final, 401 event refresh, fallback after an early end and a 5xx, no fallback after a cancel; error mapping and Retry-After |
| [card.test.ts](../../../extension/test/card.test.ts) (jsdom) | Deltas grow, full meaning wins; sentence order Meaning, Words, target language last; row closed by default, open when set, named after the language; icon-only buttons with labels; Listen reads the sentence; word card (headword, IPA, chip, example, Vietnamese last, no row for other languages, not-a-word message); guest card with and without the sign-in action; Try again only for errors that can change |
| [placement.test.ts](../../../extension/test/placement.test.ts) | Icon below the end of the line, page scroll, right and bottom edges; card below, right edge, above when there is no room |
| [settings.test.ts](../../../extension/test/settings.test.ts) | Defaults, saved and broken values, save, how the row starts in each mode |
| [settingsView.test.ts](../../../extension/test/settingsView.test.ts) (jsdom) | The 9 languages, the saved one picked, three options with Collapsed checked, saving a mode and a language, the footer names the language |
| [explain.test.ts](../../../extension/test/explain.test.ts) | Word vs stream routing, event order, one error event, no request for empty or long text |

## Change checklist

- A new target language in `TargetLanguage.java`: add it to [languages.ts](../../../extension/src/shared/languages.ts).
- A new stream event or field: [api.ts](../../../extension/src/shared/api.ts) `stream`, and doc-4.
- A new error `code`: `errorFrom` in [api.ts](../../../extension/src/shared/api.ts).
- The card's colours: [styles.ts](../../../extension/src/content/styles.ts) copies the app palette (`docs/design/design-system.md`); change both.
- A new permission or host: [scripts/build.mjs](../../../extension/scripts/build.mjs) `manifest()`.

## Known gaps

- A word's target-language row exists only for Vietnamese (the dictionary has no other language). The iOS app translates on the device with Apple Translation; the extension has no such API in Brave yet.
- Sentence words have no CEFR level in the chip (the sentence API sends only the word type; the app adds the level from its bundled dictionary).
- The card does not follow text that moves after it opens (for example a page that re-flows); it stays where the selection was.

- Not in the Chrome Web Store: load it unpacked. Safari comes later inside the Mac app (doc-16).
- Selected text is sent to api.loffy.me and Claude (personal text is never cached on the server, but it is still sent). The manifest description says so.
