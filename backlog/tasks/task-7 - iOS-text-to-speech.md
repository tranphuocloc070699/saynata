---
id: TASK-7
title: 'iOS: text to speech'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-10 04:31'
updated_date: '2026-10-14 11:18'
labels:
  - ios
milestone: m-0
dependencies: []
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Learner hears the headword, each sense's simple English explanation, and each example sentence (spec R4; scope set 2026-10-14, doc-7). On-device AVSpeechSynthesizer with an English voice only in v1. Not in v1: the Vietnamese line, Grammar examples, other languages, slow speed, accent choice. Layout (mockup option A, https://claude.ai/artifact/HncVSeSodypqSk9Y6fJUxK): the word speaker stays in the header; a small speaker on each sense title row; a small speaker at the end of each example line. While a part plays its icon becomes a stop square and the text gets a soft highlight; tapping another speaker stops the first. The compact word card on the sentence screen keeps only its word speaker.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Header speaker reads the word as typed (e.g. 'ran')
- [x] #2 Each sense title row has a speaker that reads that sense's simple explanation
- [x] #3 Each example line has a speaker at its end that reads that example
- [x] #4 While speaking, the icon shows stop and tapping it stops; starting another part stops the current one
- [x] #5 Uses an English voice and works offline
- [x] #6 Each speaker has a VoiceOver label; the speech service has unit tests (text chosen per part, stop/replace behaviour)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add one @MainActor @Observable SpeechPlayer behind SpeechSynthesizing; use AVSpeechSynthesizer with en-US and a playback/spokenAudio session that deactivates after finish or cancel.
2. Add IconButton compact sizing with a 32 pt circle inside the 44 pt tap target.
3. Wire the existing icon-only controls in WordCardView, SenseTitleRow, WordExamplesSection, WordCardCompactView and SentenceHeader; use stop.fill, exact VoiceOver labels and an accent highlight for the active text.
4. Cover speak, replace, stop, finish, late callbacks and empty input with a fake synthesizer.
5. Trace doc-4 and doc-5, run the full iOS unit suite, build, and verify borrow and ran on iPhone 16 Pro (iOS 18.5).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented on branch f3/task-7. Files: Speech/SpeechPlayer.swift, SpeechSynthesizing.swift, SpeechPart.swift; Views/Shared/SpeakerButton.swift; IconButton size .compact; wired into WordCardView, SenseTitleRow, WordExamplesSection, WordCardCompactView, SentenceHeader. Tests: SpeechPlayerTests; full iOS suite passes (134 tests). Docs updated: doc-5 (Listen section), doc-4, design-system.md. Test it card: type 'ran', tap header speaker (reads ran, icon stop.fill); tap sense speaker (explanation read, highlight); tap another speaker (first stops); example speaker; sentence speaker; repeat with airplane mode on (still works, failure case: network off); on a real iPhone with silent switch on (still heard). ShareExtension/TranslationExtension folders do not exist yet on this branch; they will need the speechPlayer environment value when added.

PR: https://github.com/tranphuocloc070699/saynata/pull/36 (into feature/f3-listen)

Final verification 2026-10-14: kept every speaker as the existing IconButton (no new button component). SpeechPlayer API is speak(_:id:), stop(), currentID; empty input is ignored; en-US AVSpeechSynthesizer uses .playback/.spokenAudio and deactivates with .notifyOthersOnDeactivation. Final unit result: "✔ Test run with 127 tests in 13 suites passed after 0.468 seconds." Final simulator build: ** BUILD SUCCEEDED **. On iPhone 16 Pro (iOS 18.5), verified borrow and ran: ran remains the spoken/displayed form; word, explanation and all three example controls are present; active VoiceOver label/icon becomes Stop/stop.fill; starting another speaker replaces the active one. check_docs.py: 13 docs checked, 0 problems. TASK-7 and TASK-33 intentionally remain open for reviewer approval.

2026-10-14 review: check on the phone whether example rows look too spaced (each compact speaker keeps a 44 pt tap area). If yes, it needs a mockup before changing. Bugs found in review moved to TASK-46 (speech after leaving a screen, ducking music).

Verified 2026-10-14: developer tested on a real iPhone 13 (iOS 18) with the main build after PR #37/#38 (word, sentence, speakers, share sheet, 'an apple', media all worked); iOS unit tests 146 passed (Codex run on the Mac, iPhone 16 Pro iOS 18.5 simulator); docs check 0 problems.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Read-aloud with AVSpeechSynthesizer (en-US) for the word, each explanation and each example; stop/replace via SpeechPlayer tokens; merged in PR #36/#37; verified on the developer's iPhone and by SpeechPlayerTests.
<!-- SECTION:FINAL_SUMMARY:END -->
