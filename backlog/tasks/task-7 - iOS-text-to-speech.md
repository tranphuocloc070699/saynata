---
id: TASK-7
title: 'iOS: text to speech'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-10 04:31'
updated_date: '2026-10-14 06:55'
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
- [ ] #1 Header speaker reads the word as typed (e.g. 'ran')
- [ ] #2 Each sense title row has a speaker that reads that sense's simple explanation
- [ ] #3 Each example line has a speaker at its end that reads that example
- [ ] #4 While speaking, the icon shows stop and tapping it stops; starting another part stops the current one
- [ ] #5 Uses an English voice and works offline
- [ ] #6 Each speaker has a VoiceOver label; the speech service has unit tests (text chosen per part, stop/replace behaviour)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add Speech/SpeechPlayer (@MainActor @Observable: speak(_:), stop(), currentID) behind a SpeechSynthesizing protocol; AppleSpeechSynthesizer wraps AVSpeechSynthesizer (en-US, .playback/.spokenAudio session, delegate with per-utterance token so replaced utterances are ignored).
2. Add SpeechPart (id, text, kind) with factories: word (as typed), explanation, example, sentence.
3. IconButton gets a compact size (32 pt circle, 44 pt tap target). New SpeakerButton composes it (listen/stop icon and VoiceOver label) plus a soft highlight modifier.
4. Wire WordCardView header, SenseTitleRow, WordExamplesSection, WordCardCompactView, SentenceHeader. Inject player in app root via environment (safe silent default for previews).
5. Unit tests with a fake synthesizer. Update doc-5 and doc-4 pages. Run tests, check_docs, open PR.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented on branch f3/task-7. Files: Speech/SpeechPlayer.swift, SpeechSynthesizing.swift, SpeechPart.swift; Views/Shared/SpeakerButton.swift; IconButton size .compact; wired into WordCardView, SenseTitleRow, WordExamplesSection, WordCardCompactView, SentenceHeader. Tests: SpeechPlayerTests; full iOS suite passes (134 tests). Docs updated: doc-5 (Listen section), doc-4, design-system.md. Test it card: type 'ran', tap header speaker (reads ran, icon stop.fill); tap sense speaker (explanation read, highlight); tap another speaker (first stops); example speaker; sentence speaker; repeat with airplane mode on (still works, failure case: network off); on a real iPhone with silent switch on (still heard). ShareExtension/TranslationExtension folders do not exist yet on this branch; they will need the speechPlayer environment value when added.

PR: https://github.com/tranphuocloc070699/saynata/pull/36 (into feature/f3-listen)
<!-- SECTION:NOTES:END -->
