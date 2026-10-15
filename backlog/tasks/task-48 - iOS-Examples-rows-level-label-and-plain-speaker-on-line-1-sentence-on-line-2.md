---
id: TASK-48
title: >-
  iOS: Examples rows: level label and plain speaker on line 1, sentence on line
  2
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 10:50'
updated_date: '2026-10-15 06:26'
labels:
  - ios
  - ui
dependencies:
  - TASK-7
priority: medium
type: task
ordinal: 47000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer's phone test (2026-10-14): the Examples row is too narrow, because the level label, the sentence and a round compact speaker share one line (WordExamplesSection.swift, Grid with 3 columns), so the sentence wraps every 2-3 words. New layout from the developer: line 1 = level label + a plain speaker icon (no circle, no border, no shadow); line 2 = the example sentence at full width. Mockup: https://claude.ai/artifact/TeVF3xAmV8KgXGzB2VGDKV (Option A: speaker at the right edge, recommended; Option B: speaker right after the label). WAIT for the developer's choice of A or B before coding (CLAUDE.md: mockup before UI code). Keep a 44 pt tap area, VoiceOver labels, stop.fill while playing (accent colour) and the soft highlight on the sentence. Add a plain style to IconButton (for example IconButton.Style.plain) instead of a new component, and list it in docs/design/design-system.md. Start after Codex's fix/device-no-response branch is merged.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each example shows the level label and a plain speaker icon on line 1, and the sentence on line 2 at full width (layout per the chosen mockup option)
- [ ] #2 The plain speaker has no circle, border or shadow, keeps a 44 pt tap area, a VoiceOver label, and shows stop.fill in the accent colour while playing
- [ ] #3 Old data with no level still shows the sentence and the speaker
- [ ] #4 design-system.md and doc-5 (Word lookup) are updated with the new style and line numbers
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add IconButton.Style.plain (+ isActive accent). 2. Rebuild WordExamplesSection: line 1 label + speaker at the right edge, line 2 sentence full width (option A). 3. List the style in design-system.md, update doc-5. 4. Screenshot on iPhone 16 Pro.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-14: the developer chose mockup Option A (label left, plain speaker at the right edge, sentence on line 2). Build A.

Implemented. 148 tests pass on iPhone 16 Pro (18.5); screenshot of the Examples row checked on the simulator. Test it card: see the PR body (look up 'depend', open Examples: label left, plain speaker right, sentence on line 2; with music playing, tapping a speaker ducks it; speech stops on new search, closing the word sheet, and going to Home).
<!-- SECTION:NOTES:END -->
