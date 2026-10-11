---
id: TASK-41
title: 'iOS: sentence result as cards (like the word card)'
status: To Do
assignee: []
created_date: '2026-10-11 15:36'
labels:
  - ios
dependencies: []
priority: high
ordinal: 41000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer approved the 2026-10-11 mockup: the sentence result uses the word-card style. The sentence on top (serif) with icon-only Listen and Copy; a Meaning card with the simple explanation and a folded target-language row (closed); a Words section where each word is a small card with the word, a form pill (past tense of blur, plural of edge) and a level chip when the word is in the bundled dictionary, its short explanation, and a tap to open the full word card.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sentence header, Meaning card with folded target-language row, and word cards match the approved mockup
- [ ] #2 Form pill and level chip come from the bundled dictionary when the word is there; otherwise the backend base-form note is used and no chip is shown
- [ ] #3 Tapping a word card opens its full word card
- [ ] #4 Icon-only buttons with accessibility labels; Vietnamese/target row last and closed
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
