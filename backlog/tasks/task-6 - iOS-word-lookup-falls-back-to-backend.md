---
id: TASK-6
title: 'iOS: word lookup falls back to backend'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-10 04:31'
updated_date: '2026-10-11 15:24'
labels:
  - ios
milestone: m-0
dependencies:
  - TASK-4
  - TASK-5
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Full lookup order: bundle -> local cache -> GET /api/words/{word} -> save locally (spec R2).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Word missing from bundle and local cache calls /api/words and shows the entry
- [x] #2 Result is saved to the local cache
- [x] #3 Errors (422, 502, offline) show a clear message
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. BackendClient.entry(forWord:): local cache (CacheKeys.wordKey) then GET /api/words/{word}, save raw JSON; decode {word, entry.senses} into WordEntry.
2. WordLookup (bundle -> BackendClient): empty senses throws WordLookupError.notAnEnglishWord; 400 gets a short message.
3. Tests with the URLProtocol stub (hit, miss+save, cache hit no network, 400/422/502, empty senses).
4. Update doc-5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done in PR https://github.com/tranphuocloc070699/saynata/pull/11 (branch f1/task-6-19). BackendClient.entry(forWord:) = local cache (CacheKeys.wordKey) then GET /api/words/{word}, saves the raw JSON (kind .word). WordLookup = bundle, then backend. Empty senses -> "not an English word"; 400 -> "Type one English word, with letters only."; 422/502/offline show the same text as the sentence path. 56 unit tests pass (iPhone 17e). Live on the simulator: "ran" -> run (bundle), "serendipity" -> card from backend, cached row saved in result-cache.sqlite, "asdkj" -> not-a-word message.

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

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Word lookup falls back to GET /api/words and saves the answer locally; clear errors (PR #11). Verified: WordLookupTests, simulator check.
<!-- SECTION:FINAL_SUMMARY:END -->
