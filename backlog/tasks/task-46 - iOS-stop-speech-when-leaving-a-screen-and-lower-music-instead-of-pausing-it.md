---
id: TASK-46
title: 'iOS: stop speech when leaving a screen, and lower music instead of pausing it'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 10:47'
updated_date: '2026-10-15 06:26'
labels:
  - ios
  - bug
dependencies:
  - TASK-7
priority: medium
type: bug
ordinal: 45000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Two F3 Listen bugs found in review (2026-10-14). 1) SpeechPlayer (EnglishTranslation/EnglishTranslation/Speech/SpeechPlayer.swift) keeps reading after the user leaves the result (new search, sheet closed, app sent to background). 2) AppleSpeechSynthesizer (Speech/SpeechSynthesizing.swift) sets AVAudioSession .playback/.spokenAudio without .duckOthers, so Spotify or Music pauses instead of getting quieter. No layout change, so no mockup. Wait until the device-fix branch from Codex (fix/device-no-response) is merged, to avoid touching the same files.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Speech stops when the shown result changes (new word or sentence), when SentenceWordSheet closes, and when the app goes to the background
- [ ] #2 Speech started inside the share sheet stops when the sheet closes (already done in SelectedTextResultView; keep it)
- [ ] #3 Audio session uses .playback, .spokenAudio with .duckOthers; other audio gets quieter while speaking and comes back to full volume after (notifyOthersOnDeactivation)
- [ ] #4 Unit test: a stop hook (e.g. a method the views call) clears currentID and calls the synthesizer's stop
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add .duckOthers to the audio session. 2. Call SpeechPlayer.stop() when the result changes (TranslateView onChange of TranslationState.resultKey), when SentenceWordSheet closes, and on scenePhase background. 3. Unit tests. 4. Update doc-5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented. 148 tests pass on iPhone 16 Pro (18.5); screenshot of the Examples row checked on the simulator. Test it card: see the PR body (look up 'depend', open Examples: label left, plain speaker right, sentence on line 2; with music playing, tapping a speaker ducks it; speech stops on new search, closing the word sheet, and going to Home).

PR: https://github.com/tranphuocloc070699/saynata/pull/54
<!-- SECTION:NOTES:END -->
