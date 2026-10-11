---
id: TASK-27
title: 'iOS: word card screen on the design system'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-11 08:36'
updated_date: '2026-10-11 09:35'
labels:
  - ios
dependencies: []
references:
  - docs/design/design-system.md
  - >-
    backlog/decisions/decision-2 -
    Word-card-usage-patterns-replace-word-partners-and-simpler-words.md
priority: high
ordinal: 27000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The word card is the main thing a learner sees for a word, and the same card (compact form) is reused for each word in a sentence result (CLAUDE.md product rule). TASK-25 built the Sage and Teal design system but no screen uses it yet. decision-2 replaced the Word partners and Simpler words chip rows with How to use it (usage patterns: pattern plus one example). The card can be built with sample data now, so it does not wait for the lookup path (TASK-4, 6) or the usage data (TASK-24).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Full card shows the word, IPA, and per sense: part of speech, CEFR level, simple English definition, examples, and How to use it (2-3 pattern + example items)
- [ ] #2 The Vietnamese line is the last row, collapsed by default, and opens on tap
- [ ] #3 A compact variant exists for the word list inside a sentence result
- [ ] #4 Uses design-system tokens and components only; buttons are icon-only with accessibility labels
- [ ] #5 SwiftUI previews cover a normal word, a word with many senses, and missing optional fields
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Models WordEntry/Sense/Usage in Models/WordEntry.swift (Codable-free, Identifiable). 2. Samples in WordEntry+Samples.swift. 3. Views/Word: WordCardView (full), WordCardCompactView, WordUsageSection (separate subview). 4. Previews: normal, many senses, missing optional fields. 5. Add to gallery, update docs, tests for model/sample.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built WordCardView, WordCardCompactView, WordUsageSection with sample data. Test it: open previews in WordCardView.swift / WordCardCompactView.swift and DesignSystemGallery; Vietnamese row last and closed. Unit tests pass.
<!-- SECTION:NOTES:END -->
