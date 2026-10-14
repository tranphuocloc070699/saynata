---
id: TASK-7
title: 'iOS: text to speech'
status: To Do
assignee: []
created_date: '2026-10-10 04:31'
updated_date: '2026-10-14 06:28'
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
