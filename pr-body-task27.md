## Test it
**Goal:** See the word card (full and compact) built on the design system, with sample data.
**Steps:**
1. Open `WordCardView.swift` in Xcode and show the canvas. Check the previews: "Normal word", "Usage labels", "Many senses", "Missing optional fields".
2. Open `WordCardCompactView.swift` and check the "Compact" preview.
3. Open `DesignSystemGallery.swift`, run its preview, scroll to "Word card".
4. Tap "More examples", "How to use it", then "Vietnamese".
**Expected:** The meaning block comes first; Vietnamese is the last row and closed at start. Opening one row closes the other. In "Usage labels", collocation/phrase/idiom items show a small label; plain patterns show none. "Missing optional fields" has no IPA, no chip level, no rows. The speaker button reads "Listen" in VoiceOver. Failure case: the sparse word must not crash or show empty rows.
**Automated:** `xcodebuild test -project EnglishTranslation/EnglishTranslation.xcodeproj -scheme EnglishTranslation -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -only-testing:EnglishTranslationTests` (all pass).

## What changed
- `Models/WordEntry.swift`: `WordEntry`, `Sense`, `Usage` (with `Usage.Kind`: pattern, collocation, phrase, idiom; default `.pattern`).
- `Models/WordEntry+Samples.swift`: depend, get, run, zeitgeist samples.
- `Views/Word/`: `WordCardView`, `WordCardCompactView`, `WordUsageSection` (separate subview).
- Gallery now shows the real cards. Feature doc-5 updated. Tests in `WordEntryTests.swift`.

## Notes
- TASK-4 also adds model types and `Usage.Kind`. Expect a merge clash in `WordEntry.swift`: keep one definition.
- Not wired into the main input flow (TASK-19).
- Follow-up: the "Show translation" setting is not applied yet; Vietnamese is always collapsed.
- Full test run also starts the UI test target, which crashed here (simulator "server died"); unit tests pass.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
