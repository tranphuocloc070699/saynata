---
id: TASK-72
title: Better 'Did you mean' spelling suggestions
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 11:41'
updated_date: '2026-10-15 11:41'
labels:
  - ios
dependencies: []
ordinal: 72000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Typing 'controlvesation' shows 'not an English word' with no suggestions, because SpellingSuggester only allows distance 1 (up to 4 letters) or 2 and never asks Apple's checker. 'conversation' is 5 away. Apple's UITextChecker is good for normal typos (recieve, beatiful, develoment, runj) but suggests nothing for this word. Reported by the developer on 2026-10-15. Scope stays the single-word 'not an English word' result; SpellingSuggestionsView and the layout do not change.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 UITextChecker guesses (en_US, single lowercase words, not the typed word) are used behind a protocol so tests use a fake
- [ ] #2 Max distance grows with length: 4 letters or fewer 1, 5 to 8 letters 2, 9 or more floor(length/3); bundled matches need the same first letter and a length difference within the max
- [ ] #3 Merge order: checker guesses that are bundled words, then bundled close matches, then other checker guesses; no duplicates, max 3
- [ ] #4 controlvesation suggests conversation; runj suggests run first; recieve suggests receive; a correct bundled word gives nothing
- [ ] #5 Tests cover the cases above and the distance rule for 4, 8 and 15 letters; doc-5 is updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add SpellChecking protocol + UITextChecker implementation (SpellingSuggester.swift).
2. New distance rule (1 / 2 / floor(len/3)), shared first letter, length gap rule.
3. Merge: checker bundled guesses, close bundled matches, other checker guesses; dedupe; cap 3.
4. Wire UITextChecker in TranslatorFactory (TranslationStore untouched).
5. Tests with a fake checker + real bundle; update doc-5; check_docs.
<!-- SECTION:PLAN:END -->
