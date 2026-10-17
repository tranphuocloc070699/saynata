---
id: doc-21
title: Explain copied text
type: specification
created_date: '2026-10-17 06:39'
updated_date: '2026-10-17 08:13'
---
# Explain copied text (Back Tap and Action Button)

> Status: **built** (TASK-87). Tested on the iPhone 17e (iOS 26.3) simulator from the Shortcuts app. The developer's iPhone 13 (iOS 26.5) runs the shortcut from Back Tap. Last traced from code: 2026-10-17.
> Spec: R1, R2 in [Vision and spec](../doc-1%20-%20Vision-and-spec.md). Builds on [Word lookup](doc-5%20-%20Word-lookup.md) and [Sentence translation](doc-4%20-%20Sentence-translation.md).

## What it does

The user copies text in any app, then double-taps the back of the phone (Back Tap) or presses the Action Button. Saynata opens with the copied text in the input and explains it at once, the same as typing it. With nothing copied (or the paste refused), the input is empty and has the keyboard, so the user types.

Back Tap works on an iPhone 8 or later (iOS 14+), the Action Button on an iPhone 15 Pro or later.

## Why it opens the app (and is not a card over the current app)

The first version (same task, same day) showed a Shortcuts card over the current app without opening Saynata. On the developer's phone (iOS 26.5) it read nothing: a background App Intent cannot show iOS's "Allow Paste" question, so the paste was refused silently, and the "Paste from Other Apps" setting never appeared (iOS adds it only after the first question). The card also had to copy the theme colors as numbers and was cut at a fixed height on iOS 26. The developer chose to open the app instead (doc-7, 10-17). The app reads the clipboard while it is on screen, so iOS asks normally.

## Big picture

```mermaid
flowchart TD
    TRIGGER["Back Tap / Action Button / Siri / Spotlight"] --> INTENT["ExplainCopiedTextIntent (openAppWhenRun = true)"]
    INTENT --> LAUNCH["CopiedTextLaunch.request(): requestCount + 1"]
    LAUNCH --> VIEW["TranslateView .task(id: requestCount)"]
    VIEW --> CLIP["SystemClipboard (Allow Paste question)"]
    CLIP --> STORE["TranslationStore.startFromClipboard"]
    STORE -- "text" --> PASTE["inputText = cleaned text, translate() (word or sentence, as usual)"]
    STORE -- "empty or refused" --> BLANK["input and result cleared, keyboard on the input"]
```

## Step by step

| Step | File | What happens |
|---|---|---|
| 1 | [ExplainCopiedTextIntent.swift:21](../../../EnglishTranslation/EnglishTranslation/Intents/ExplainCopiedTextIntent.swift#L21) | `openAppWhenRun = true`: iOS opens Saynata, then `perform()` calls `CopiedTextLaunch.shared.request()`. |
| 2 | [CopiedTextLaunch.swift:31](../../../EnglishTranslation/EnglishTranslation/Translation/CopiedTextLaunch.swift#L31) | An `@Observable` counter. A count (not a flag) so two Back Taps in a row are both seen. |
| 3 | [TranslateView.swift:45](../../../EnglishTranslation/EnglishTranslation/Views/Translate/TranslateView.swift#L45) | `.task(id: requestCount)` runs on each request, and also on a cold start (the count is already 1). If a sign-in sheet was open it waits 0.5 s before focusing, since a closing sheet takes the focus. |
| 4 | [ExplainCopiedTextIntent.swift:29](../../../EnglishTranslation/EnglishTranslation/Intents/ExplainCopiedTextIntent.swift#L29) | `SystemClipboard`: `hasStrings` first (never asks), then `UIPasteboard.string` (asks "Allow Paste"). Text there but `string` nil means the user refused. |
| 5 | [TranslationStore.swift:132](../../../EnglishTranslation/EnglishTranslation/Stores/TranslationStore.swift#L132) | Closes a leftover sign-in sheet, cleans the text (`CopiedText`, drops the Apple Books citation), puts it in the input and calls `translate()`. Otherwise clears the input and result and returns true (keyboard). |
| 6 | [SaynataShortcuts.swift:12](../../../EnglishTranslation/EnglishTranslation/Intents/SaynataShortcuts.swift#L12) | The App Shortcut, so iOS lists "Explain copied text" in Shortcuts, Spotlight, Siri, Back Tap and the Action Button with no setup. |

## Setup on the phone

- **Back Tap:** Settings, Accessibility, Touch, Back Tap, Double Tap, then **Explain copied text**.
- **Action Button** (iPhone 15 Pro and later): Settings, Action Button, Shortcut, then Saynata, **Explain copied text**.
- **No paste question each time:** after the first question, Settings, Apps, Saynata, Paste from Other Apps, Allow. On iOS 26 the highlighted button of the question is Don't Allow Paste.

## The app is not built into the extensions

`Intents/` is in the membership exceptions of ShareExtension, TranslationExtension and SaynataMac in `project.pbxproj`: an `AppShortcutsProvider` belongs to the app only, and `SystemClipboard` uses UIKit. `CopiedTextLaunch` and `startFromClipboard` compile everywhere.

## Errors

| Cause | What the user sees |
|---|---|
| Nothing copied, or only spaces | Empty input with the keyboard |
| Don't Allow Paste | Empty input with the keyboard |
| Over 500 characters | The text in the input with "Make it shorter", no request |
| Guest, answer not cached | The text in the input and the sign-in sheet (doc-17) |
| Anything else | The normal error under the input, as when typing |

## Tests

| Test file | What it covers |
|---|---|
| `EnglishTranslationTests/StartFromClipboardTests.swift` | A copied word is pasted and looked up, a sentence is explained, the Books citation is left out, empty / refused / spaces clear the input and the result, close the sign-in sheet and ask for the keyboard, each request is counted |

Run: `xcodebuild test -project EnglishTranslation/EnglishTranslation.xcodeproj -scheme EnglishTranslation -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.5' -only-testing:EnglishTranslationTests -parallel-testing-enabled NO`.

## Change checklist

- A new file in `Intents/`: add it to the three membership exception lists.
- `TranslateView` or the input focus changes: keep the `.task(id: launch.requestCount)` and the focus after the sheet closes.

## Known gaps

- iOS asks "Allow Paste" every time until the user sets Paste from Other Apps to Allow.
- The app opens on the text; going back to the app you were in is the system "◀ App" link at the top left.
