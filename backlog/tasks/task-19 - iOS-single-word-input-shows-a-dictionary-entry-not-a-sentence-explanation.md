---
id: TASK-19
title: 'iOS: single-word input shows a dictionary entry, not a sentence explanation'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-10 06:36'
updated_date: '2026-10-11 09:55'
labels:
  - ios
dependencies:
  - TASK-4
  - TASK-6
  - TASK-24
  - TASK-27
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Today every input goes to POST /api/translations, so one word like 'Graveyard' gets a sentence explanation where Meaning and Words say the same thing. Spec R1: one token after normalising is a Word and uses the dictionary path (bundle, local cache, GET /api/words). It needs its own result view built from WordEntry senses.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Input with one token goes to the word lookup path; more than one token goes to /api/translations
- [ ] #2 Token rule matches backend CacheKeys normalising (unit tests)
- [ ] #3 Word result shows each sense: pos, CEFR, simple definition, examples, and How to use it (usage patterns, decision-2); the Vietnamese line comes last
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #2 Tests pass
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. CacheKeys.isSingleToken (same rule as backend normalising).
2. TranslationStore: one token -> WordLooking path, state .loadedWord(WordEntry); more tokens -> sentence path as before.
3. TranslateResultView shows WordCardView; Vietnamese row stays last and closed.
4. Tests for the token rule and the store path. Update doc-4, doc-5, CLAUDE.md if needed.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done in PR https://github.com/tranphuocloc070699/saynata/pull/11 (branch f1/task-6-19). CacheKeys.isSingleToken (normalise like the backend, no space left). TranslationStore.translate routes one token to WordLooking (state .loadedWord -> WordCardView) and the rest to the sentence translator as before. Existing store tests used "Hello" as a sentence; they now use "Hello there". doc-3, doc-4, doc-5 updated (line numbers, flow). Follow-ups: a token with punctuation ("Hello.") goes to the word path and gets the 400 message; strip punctuation first. Backend words have no IPA.

## Test it
**Goal:** Type one word and get a dictionary card (bundle, then local cache, then backend); type a sentence and get the sentence explanation as before.

**Start the backend first** (only the backend word in step 2 needs it):
1. Docker Desktop must be running. In `backend/`: `cp .env.example .env` once, put your `CLAUDE_API_KEY` in `.env`.
2. `cd backend && ./mvnw spring-boot:run` (port 8081; Spring starts Postgres from `compose.yaml`).
3. The app reads the address from `BackendBaseURL` in `EnglishTranslation/EnglishTranslation/Info.plist` (it is `http://localhost:8081`; the simulator reaches your Mac as localhost). Nothing to change on the simulator.
4. Run the app (Cmd+R, iPhone 17e or any simulator), keep the "Server" provider selected.

**Steps:**
1. Type `ran`, tap Explain.
2. Type `serendipity` (not in the NGSL bundle), tap Explain. Costs one Claude call, about a cent.
3. Stop the backend (Ctrl+C) or turn the Mac's Wi-Fi off. Type `serendipity` again, tap Explain.
4. Type `asdkj`, tap Explain (backend must be running; one more small Claude call the first time).
5. Type `hello!` and tap Explain.
6. Type `She ran to catch the bus.` and tap Explain (backend running).

**Expected:**
1. The card shows **run** (not "ran"), IPA, level chips per sense, the meaning, "More examples", "How to use it" (if the bundle has usage), and a **Vietnamese** row last and closed. No network is needed (try it with the backend off).
2. A spinner for a few seconds, then a card for **serendipity** (no IPA: the backend does not send it). Vietnamese row last and closed.
3. The same card appears at once, with the backend off. The answer came from the local cache. (The backend log shows no second request.)
4. Red message: "asdkj" is not an English word. Check the spelling. No empty card.
5. Red message: Type one English word, with letters only. (failure case: the backend rejects `!`)
6. The sentence explanation as before: Meaning, Words, then Vietnamese. No word card.
Other failures: a first-time word with the backend off shows the system offline message; Claude down shows "The translation service is not available. Try again later. (status 502)".

**Automated:** 56 unit tests (token rule, store route, lookup order, cache save and reuse, errors). Command:
`xcodebuild test -project EnglishTranslation/EnglishTranslation.xcodeproj -scheme EnglishTranslation -destination 'platform=iOS Simulator,name=iPhone 17e' -only-testing:EnglishTranslationTests -parallel-testing-enabled NO` (read the last "Test run with N tests" line).
<!-- SECTION:NOTES:END -->
