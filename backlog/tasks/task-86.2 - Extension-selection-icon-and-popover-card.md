---
id: TASK-86.2
title: 'Extension: selection icon and popover card'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 09:00'
updated_date: '2026-10-16 09:54'
labels:
  - extension
dependencies:
  - TASK-86.1
parent_task_id: TASK-86
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The visible part: a small icon next to selected text, and the card in a Shadow DOM so page CSS cannot break it. Starts from the mockup the developer picks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Icon appears near a text selection and goes away when the selection is cleared or on Escape
- [x] #2 Card order: quoted text, simple English meaning, words, target language row last and collapsed by default
- [x] #3 Meaning grows word by word from meaning-delta events
- [x] #4 Icon-only buttons with aria-labels; speaker uses the Web Speech API
- [x] #5 Card lives in a closed Shadow DOM and works on sites with strict CSP
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. shared/settings.ts: target language, Show translation mode, last row state (chrome.storage.sync), rowStartsOpen like the iOS TranslationRowPreference.
2. content/: closed Shadow DOM host; icon A (round teal S) at the end of the selection; card rendered from a state (loading, growing meaning, sentence, word, error, sign-in) with icon-only buttons (Listen, Copy, Close) and the folded target-language row last.
3. Placement helpers (icon and card stay inside the viewport, card above when there is no room below) as pure functions with tests.
4. Speech with speechSynthesis (en-US).
5. Vitest + jsdom tests for card order and fold state; a browser check with a harness page that fakes chrome.*.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Checked in the in-app browser with a harness page that loads dist/content.js and fakes chrome.runtime/storage: icon after a real drag and a double-click, sentence card with growing meaning then Words and Vietnamese, word card, Vietnamese row opens on click, Escape closes, guest lock card. Fixed: the pop animation re-ran on every render (now only on open), the browser focus ring on the card. Closed Shadow DOM with inline <style>; page CSP does not apply to content-script DOM. 64 Vitest tests pass.

PR: https://github.com/tranphuocloc070699/saynata/pull/91 (into feature/f7-browser-extension; merge order #90, #91, #92, #93).
<!-- SECTION:NOTES:END -->
