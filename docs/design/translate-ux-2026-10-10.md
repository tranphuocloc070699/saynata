# Saynata UX research: fewer steps (2026-10-10)

Focus: UX first, UI later. Goal: fewer taps and less thinking for each core task.
A "step" below means one tap or one required decision. Typing is counted once as "type".

## 1. Summary

- Today the only built screen is Translate. Translating costs 2 taps plus typing, but the screen shows two pickers (provider, language) before the user can do anything. Listen, Speak and History do not exist yet, so we can design them short from the start.
- Biggest wins: (1) one input-first screen where words and sentences use the same box and the app decides what to do; (2) auto-detect direction, so no language picker in the normal case; (3) speaker and mic buttons inside the result, not on other screens; (4) recents shown under the input on the empty state, plus a one-tap save star.
- Copy the Claude pattern for the shell: empty home = greeting + bottom composer. Copy Apple/Speak for the result: a card with speaker, copy and save in one top row.
- Practice should be inline: tap the mic on the result card, speak, see the score on the same card. Do not open a separate "practice" screen for the first version.
- Cheap entry points outside the app (share sheet, Look Up action, Control Center / Action button) can save the most steps, but cost more work. Do them after the core screen.

## 2. Current-flow audit

Code read: `EnglishTranslation/EnglishTranslation/Views/Translate/` and `Stores/TranslationStore.swift`. Docs read: features overview, doc-4 (sentence), doc-5 (word lookup), spec doc-1.

What the screen is today ([TranslateView.swift:15-22](../../EnglishTranslation/EnglishTranslation/Views/Translate/TranslateView.swift#L15-L22)):

```
+--------------------------------+
| [ Server | Direct ]  provider  |  <- developer switch, always visible
| Translate into: Vietnamese  v  |
| +----------------------------+ |
| | TextEditor (140 pt)        | |
| +----------------------------+ |
| [ Explain ]                    |
| "Type a sentence, then tap     |
|  Explain."                     |
| (result: Meaning / Words /     |
|  Vietnamese)                   |
+--------------------------------+
```

| Core task | Steps today | Notes (file:line) |
|---|---|---|
| Translate a sentence | **2 taps + type**: tap field, type, tap Explain | Field is not focused on launch, so a tap is needed ([TranslateInputView.swift:16](../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateInputView.swift#L16)). Return key adds a new line; only the button sends ([TranslateInputView.swift:21-25](../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateInputView.swift#L21-L25)). The button is above the result, so it scrolls with the page. |
| Change target language | **2 taps** (open menu, choose) | Menu picker always on screen ([TranslateOptionsView.swift:22-27](../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateOptionsView.swift#L22-L27)). Default is Vietnamese ([TranslationStore.swift:16](../../EnglishTranslation/EnglishTranslation/Stores/TranslationStore.swift#L16)). |
| Translate a word | **Same 2 taps + type**, but it goes to the sentence endpoint | No word screen, no bundled dictionary reader, no local cache yet (doc-5 "Known gaps"). Every tap is a network call, even for a repeat. |
| Listen to pronunciation | **Not possible** | No TTS code. Spec R4 only. |
| Practise speaking, get a score | **Not possible** | No STT code. Spec R5 only. If built as a separate screen it is about 5 steps (see section 5). |
| Revisit history or saved | **Not possible**. Retype = 2 taps + type | No store of past results. Local cache (TASK-5) is not built, and a cache is not a history list anyway. |
| Read the result | Scroll | Order is Meaning, Words, then the translation at the bottom ([TranslateExplanationView.swift:15-30](../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateExplanationView.swift#L15-L30)). The thing the user asked for comes last. |

Other friction found in code:
- Provider picker (Server / Direct) is a developer tool, shown to every user ([TranslateOptionsView.swift:15-20](../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateOptionsView.swift#L15-L20)).
- Result heading follows the picker, not the request, so it can show the wrong language (doc-4 known gap).
- Loading is a bare spinner, error text is plain red ([TranslateResultView.swift:18-23](../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateResultView.swift#L18-L23)). No retry button.
- Empty state is only a hint line ([TranslateResultView.swift:16](../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateResultView.swift#L16)).

## 3. Reference findings

Search notes: Google Translate and DeepL did not return results on Mobbin iOS (0 matches for DeepL; the Google query only returned Google Lens). I used Apple's system Translate sheet, LINE, Speak, Duolingo, CapWords and others instead. All searches used `platform: ios`. No `ai_usage_notice` was returned.

Searches run: Claude home/composer; Google-style translator home; dictionary word detail with audio; pronunciation practice with mic; saved words and recents; DeepL-style result (empty); translator with two language selectors; pronunciation score/feedback; Claude response actions; share sheet / widget lookup; flow "look up word, hear it, save it".

### 3.1 Input-first home (Claude style)

| App | Screen | Good | Copy |
|---|---|---|---|
| Claude | [Empty home with composer](https://mobbin.com/screens/3ecbf36f-0aea-4f7c-846b-703ce03bd2ab) | One greeting, one input at the bottom, nothing else. Mic is next to send. Settings live behind a "+" and a menu. | Saynata home = composer at the bottom, thumb reach, keyboard opens at launch. Move language and provider out of the main view. |
| Claude | [Chats list with search + New chat](https://mobbin.com/screens/4da9911e-c032-439b-82cb-c5ebfadf811f) | History is one tap away from the left menu, search is at the bottom. | History screen: list + bottom search. |
| Claude | [Response with action row](https://mobbin.com/screens/70406cbc-cb17-4db8-8176-057a65dc5f56) | A small row of icons under the answer: copy, share, play (read aloud), like, retry. One tap each, no menu. | Same row under every result: speaker, copy, save, mic. |
| Claude | [Reply composer under a long answer](https://mobbin.com/screens/9f5702c8-bcad-4b9e-a02b-50baa90a3bc3) | The composer stays pinned while the answer scrolls. | Keep the input pinned, so the next lookup needs no scroll back. |
| Singapore Airlines | [Translation Assistant](https://mobbin.com/screens/9975067b-7c96-4cc3-af87-3ac651cd8c7f) | Two big cards, each with its own language chip and mic. Only a small chip to change language. | Language as a small chip on the input, not a form field. |

### 3.2 Auto-detect and language choice

| App | Screen | Good | Copy |
|---|---|---|---|
| Apple Messages | [Translate sheet](https://mobbin.com/screens/dc4909db-9e9c-42f6-a39b-d6ec45c87e90) | "Detected as English (US)" shows above the text. One play button, "Copy Translation", "Add to Favorites". Zero setup. | Show "Detected: English" as small text. Result + play + copy + save on one sheet. |
| Apple Freeform | [Translate sheet](https://mobbin.com/screens/c9b89267-79ec-4a34-b55e-3a9bb9a34e75) | Same sheet, from the text-selection menu. | Shows the system "Translate" entry point exists in every app. |
| LINE | [Detected + Translate to Japanese](https://mobbin.com/screens/c5ef5f55-9291-4070-8c6c-17c4f14cf427) and [result with Copy / Share](https://mobbin.com/screens/5f611e4c-b8b4-4799-99d6-bc20746c2f2c) | One tap to translate, language is a small control at the bottom, Copy and Share on the same sheet. | Language switch at the bottom of the result, not at the top of the form. |
| Fabric | [Language list with Auto first and search](https://mobbin.com/screens/f77b95ea-1449-4749-b8ef-3decde9f8b9e) | "Auto" is the first row, plus a search box. | If we keep a picker: Auto first, recent languages next, search for the rest (9 languages is small, but "Auto" matters). |
| TikTok | [Translate into list with radio buttons](https://mobbin.com/screens/030aaf92-aa56-4c44-973e-5a0c994f9098) | Plain full list, one tap and Done. | Fallback pattern, but it costs 3 taps (open, choose, Done). Prefer the chip. |

### 3.3 Word result with one-tap audio and save

| App | Screen | Good | Copy |
|---|---|---|---|
| Speak | [Word card in tutor chat](https://mobbin.com/screens/9e459a59-de98-48e1-8a2b-996582a566a5) (flow: [Word detail](https://mobbin.com/flows/4f243fb3-45cc-46d0-a8bd-2845d4c35b01)) | Ask a question, the answer is a small card: speaker + word + meaning + bookmark. Tap the card for more. | Compact word card as the first result. Detail only when the user asks. |
| Speak | [Word detail sheet](https://mobbin.com/screens/6cda687c-0218-4908-9d4c-86fa22cfc242) | Speaker, copy and bookmark icons in one top row. Then translation, short explanation, similar words, example. | Header row of three icons on the word detail. Order: meaning first, extras below. |
| Vocabulary | [Word with phonetic + speaker](https://mobbin.com/screens/935d4833-e999-4cb0-92df-033c996fc00d) | Word, IPA and speaker on one line, then short definition, then examples. | Same top block. Our extra data (CEFR, collocations) goes below the fold. |
| Preply | [Word card](https://mobbin.com/screens/2f73be32-b930-4169-a20f-d2cc70e53fc0) | Speaker, word, part of speech, definition, example. Trash to remove. | Simple card; swipe between saved words. |
| Fable / Matter | [Select text, tap Dictionary](https://mobbin.com/flows/6245d82b-7861-4ee0-8ef9-5a321213f399), [flow](https://mobbin.com/flows/62d05430-43cc-41c4-b3bf-8ed9937a8c8b) | iOS system Look Up: select word, one tap, sheet. 2 taps from any text. | Saynata can be that sheet via a Share extension (see 3.6). |
| Perplexity | [Selection menu: Look Up / Translate / Share](https://mobbin.com/screens/9f61959b-8ac5-497f-a58a-a6db44e100be) | Shows that users already expect "select, then act". | Context for the share-sheet idea. |

### 3.4 Speaking practice and score

| App | Screen | Good | Copy |
|---|---|---|---|
| Duolingo | [Repeat after ...](https://mobbin.com/screens/68634e56-78f8-484d-aedb-a2cb6117d216) | Sentence with speaker, one big mic button, a "Can't speak now" skip. | Big mic, one clear skip. Do not force practice. |
| CapWords | [Hold to speak](https://mobbin.com/screens/6a6db198-c314-44a6-bc3d-8d8beac946b8) and [retry hint](https://mobbin.com/screens/f42babbc-d249-4893-89c8-6b569abff84f) | Card with IPA + speaker, mic under it, short message on a miss ("Tap speaker to hear the ideal speed"). | Hold-to-speak (1 gesture, no start/stop taps). On a miss, point to the speaker. |
| Speak | [Speak now with waveform](https://mobbin.com/screens/b54b1ed9-3256-48cf-a652-e0a40442efb9) | Sheet at the bottom, live waveform, cancel on the left. | Waveform feedback proves the mic is working. |
| Speak | [Corrections](https://mobbin.com/screens/c7abdc8b-c155-424c-b22c-ac79535784e0) | Your sentence in orange, the better one in green, one sentence of why, Retry inside the card. | Our "missing / wrong words": show expected vs heard, colour the wrong words, Retry on the same card. |
| Duolingo | [Incorrect panel with answer](https://mobbin.com/screens/9c29a755-6d72-496b-81d0-497c6a4120f7) | Result panel slides up from the bottom with the right answer and one "Got it" button. | Result panel, one dismiss button. |
| Babbel | [Listen and repeat](https://mobbin.com/screens/c25e73ca-1a5d-4c99-adeb-a7158531a969) | Speaker and big mic with level rings. | Mic animation while listening. |
| Beside | [Record screen](https://mobbin.com/screens/e54bd82f-75fc-4691-a41b-7c9f63fba38b) | One prompt, one red record button. | Minimal state for the mic. |
| Microsoft Copilot | [Score screen](https://mobbin.com/screens/8ac01016-a048-4470-a81b-120c1ce491b3) | One sentence: "You scored 60%", then one button for details. | Score first, details after one tap. |
| Amazon Alexa | [Name pronunciation](https://mobbin.com/screens/2f6a5850-cbcd-4afe-aed8-37a88a2694f0) | "Press play to hear" then "Nailed it" or "Let's fix that". | Simple self-check fallback if the score is wrong. |

### 3.5 History and saved words

| App | Screen | Good | Copy |
|---|---|---|---|
| Speak | [Saved Lines: search + Recent + speaker per row](https://mobbin.com/screens/226b3b70-10ba-46c6-9acc-b7e7950a16a4) | Each row has a speaker (tap to hear without opening), a search box, and one "Review" button for practice. | Best model for Saynata's history: speaker on each row, bottom button "Practise these". |
| Vocabulary | [Saved list with heart / bookmark / share](https://mobbin.com/screens/b9e8c342-999f-4371-be01-bbce0cdcdae7) | Each row is a full mini card with IPA, speaker and definition. | Too tall for a long list; use it for a "Saved" tab only. |
| Quizlet | [Terms list with star and speaker](https://mobbin.com/screens/ca3b6042-3294-4b83-a1ab-725d0d158ce4) | Star toggle and speaker on each row. | Star = save, one tap. |
| Duolingo | [Words list with speaker](https://mobbin.com/screens/a47d0400-435b-4be7-b1ba-16cc97ad36a9) | Dense list, speaker per row, "START" button on top. | Dense rows for recents. |
| Preply | [Vocab tab with New / Learning / Learned](https://mobbin.com/screens/69f48d4c-2233-4ece-9fa3-11fd10f5569f) | Status chips, "Practice your vocabulary" card on top. | Later: learning status. Not for v1. |
| Wispr Flow | [Dictionary list + add button](https://mobbin.com/screens/f55d7a67-d935-46c2-abd9-8b17afe1ec39) | Very short list, add button. | Reminder: keep the empty/short list calm. |

### 3.6 Entry points outside the app

Mobbin shows the **iOS system Translate and Look Up sheets** ([Messages](https://mobbin.com/screens/dc4909db-9e9c-42f6-a39b-d6ec45c87e90), [Freeform](https://mobbin.com/screens/c9b89267-79ec-4a34-b55e-3a9bb9a34e75), [Fable](https://mobbin.com/screens/cc28b946-d3a3-4804-b5b9-c7ec5e07c051)) and the LINE translate sheet. These are bottom sheets that open on top of the current app: 0 app switches. I found no third-party translator keyboard or widget screens on Mobbin, so those ideas below come from Apple's docs, not from a reference:
- Share Extension: Apple's [Share extension guide](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/Share.html).
- App Intents / Shortcuts / Action button / Control Center: [App Intents](https://developer.apple.com/documentation/appintents).
- Widgets: [WidgetKit](https://developer.apple.com/documentation/widgetkit).

## 4. Recommended flows (current vs. proposed)

### 4.1 Translate a sentence
Today: tap field, type, tap Explain = **2 taps + type**.

Proposed:
1. Open app. Keyboard is already up (autofocus). *(0 taps)*
2. Type.
3. Tap send arrow (or Return, set to "Send").
= **1 tap + type. Saves 1 tap** (and the result shows the translation first).

### 4.2 Translate a word
Today: same 2 taps, network call every time.

Proposed:
1. Open app, keyboard up.
2. Type "reluctant". The app sees one token and looks up the bundled dictionary while typing; a compact word card appears under the input after a short pause (debounce, about 300 ms). Nothing to send.
= **0 taps + type. Saves 2 taps**, works offline, no waiting. Tap the card for the full entry (1 tap, optional).

### 4.3 Language choice
Today: 2 taps to change, always visible.

Proposed: auto-detect. Vietnamese in = English out. English in = Vietnamese out (the default). Show a small chip "English -> Vietnamese" on the composer. Tap the chip only to override (2 taps, same as now, but only when needed). Normal case = **0 taps, saves 2 for anyone who switches direction**. The LLM already handles Vietnamese-to-English (doc-4 probe results), so the server can accept `targetLang` guessed on the phone.

### 4.4 Listen
Today: not possible. If built the usual way: open detail, find speaker, tap, maybe three speakers (word, definition, Vietnamese) = about 3 taps.

Proposed:
1. Speaker icon on the result header (plays the English text) and on the Vietnamese line. *(1 tap)*
2. Optional setting: auto-play the word once when a word card appears (0 taps).
3. Long-press the speaker for slow speed (Duolingo has a turtle button).
= **1 tap, or 0 with auto-play.**

### 4.5 Speak and score
Today: not possible. A separate Practise screen would be: result -> tap Practise -> new screen -> tap mic -> speak -> tap stop -> read score -> tap back = about **5-6 steps**.

Proposed (inline):
1. On the result card, press and hold the mic (or tap once and auto-stop on silence).
2. Say the meaning. Live waveform shows.
3. Release. The score and marked words appear on the same card, with Retry and the speaker for the ideal version.
= **1 gesture + speak. Saves about 4 steps.** Back button not needed because there is no new screen.
Permission prompts (mic + speech) appear once, on first use. Say why, in one line, before the system dialog.

### 4.6 History and saved
Today: none; to see a past item you retype (2 taps + type).

Proposed:
1. Empty home shows the last 5 lookups under the input (like Claude's recents, with a speaker on each row as in Speak's Saved Lines). Tap a row = result again, from cache. *(1 tap)*
2. Star on every result = save. *(1 tap)*
3. "History" opens from the top-left menu, with bottom search, as in Claude. Tabs: Recent / Saved. *(2 taps)*
4. Bottom button "Practise saved words" starts a short loop of inline practice.
= **Revisit: 1 tap instead of 2 taps + type. Save: 1 tap.**

### 4.7 From other apps (later)
- Share Extension: select text in Safari or Messages -> Share -> Saynata. About 3 taps, 0 typing, no app switching if shown as a sheet.
- Action button / Control Center control (App Intent) that opens Saynata with the keyboard up: 1 tap from anywhere.
- Widget with "Word of the day" or last lookups: 1 tap to open the word.

### 4.8 States to design for each flow
- Empty: greeting + recents (not just a hint).
- Loading: for a cache or bundled hit show nothing; for an LLM call show the translation area as a skeleton, not a spinner alone.
- Error: message with a Retry button; keep the typed text.
- Offline: words still work from the bundled dictionary; say "Sentences need internet".

## 5. Recommended changes, ranked by impact / effort

| # | Change | Steps saved | Effort | Notes |
|---|---|---|---|---|
| 1 | Autofocus the input; send button and Return = send | 1 per lookup | S | Edit `TranslateInputView`. |
| 2 | Hide provider picker (debug build only); move language to a small chip; auto-detect direction | 2 per switch, plus less clutter | S-M | Detection: `NLLanguageRecognizer` from Apple's [NaturalLanguage](https://developer.apple.com/documentation/naturallanguage) works on-device. |
| 3 | Put the translation first in the result, then Meaning, then Words | 1 scroll | S | Reorder in `TranslateExplanationView`. |
| 4 | Pin the input at the bottom, result above it (composer pattern) | 1 scroll per lookup | M | Matches the Claude direction. |
| 5 | Local history (store each result) + recents under the empty input | 1-2 taps + typing per revisit | M | Also helps offline. Different from the cache in TASK-5. |
| 6 | Word path: one token goes to bundled dictionary, instant card while typing | 2 taps, 0 network | M-L | Needs the dictionary reader (doc-5 gap). |
| 7 | Speaker button on result and on each saved row (`AVSpeechSynthesizer`) | 2 taps vs the multi-screen option | S-M | Spec R4. |
| 8 | Star to save; Saved tab | New feature, 1 tap | S-M | Same store as #5. |
| 9 | Inline speak and score on the result card, hold to speak | about 4 steps vs a separate screen | L | Spec R5. Build the scoring as a pure function first (tests are easy). |
| 10 | Real error, loading, empty states with Retry | Fewer dead ends | S | |
| 11 | Share Extension; Action button / Control Center shortcut | Up to 3-4 steps (leave the app, open, type, send) | L | After the core flow is stable. |
| 12 | Widget with recents / word of the day | 1 tap re-entry | M | Later. |

Best first batch: #1, #2, #3, #10. Small edits, all inside the current Translate views, and the screen already feels shorter.

## 6. Open questions

1. Should a one-word input always use the bundled dictionary, even when the word is a typo? (Suggestion: show "Did you mean ...?" using the dictionary, then fall back to the LLM.)
2. Is Vietnamese-to-English translation a real use case, or is the user always writing English? This decides if auto-detect is worth it (4.3).
3. Auto-play audio on a word card: on by default or a setting? Auto-play surprises people in quiet places.
4. Which text does the mic score: the Vietnamese meaning of a word, or the English sentence repeated back? The spec says "say the meaning", and the Duolingo-style flow repeats the English. The score logic and the UI differ.
5. Keep any provider switch for the developer? Suggestion: a hidden menu in debug builds only.
6. History size and privacy: sentences can contain personal text (spec says shared on the server). Keep history local only?
7. Tab bar or no tab bar? Claude uses a side menu, with no bottom tabs. With only Translate, History and Saved, a menu works; add tabs only if Practise becomes its own mode.

## 7. UI direction (later, short)

Follow the Claude style the developer chose, but only after the flows above are settled: greeting + bottom composer ([Claude home](https://mobbin.com/screens/3ecbf36f-0aea-4f7c-846b-703ce03bd2ab)), warm off-white background, serif for the word and the translation, one warm accent, a small icon row under each result ([Claude actions](https://mobbin.com/screens/70406cbc-cb17-4db8-8176-057a65dc5f56)). Follow Apple's [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) for sheets, text fields and mic permission. Colour, font and spacing tokens come in a separate report.

## 8. Next steps

1. Ship batch one: autofocus, send button, translation first, hide provider picker, real states.
2. Add the history store and recents list.
3. Add auto-detect direction and the language chip.
4. Build the TTS speaker button (word, definition, Vietnamese).
5. Build the word path with the bundled dictionary.
6. Build inline speak and score.
7. Share Extension and Action button.

I can turn these into Backlog tasks if you say yes. I have not created any.
