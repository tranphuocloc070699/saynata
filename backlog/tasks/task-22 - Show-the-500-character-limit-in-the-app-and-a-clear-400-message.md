---
id: TASK-22
title: Show the 500-character limit in the app and a clear 400 message
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-10 06:37'
updated_date: '2026-10-12 03:08'
labels:
  - ios
  - backend
dependencies: []
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The backend rejects text over 500 characters with detail 'Invalid request content.' The app has no limit or counter, so the user only sees that vague message after waiting.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The input shows a character count and blocks Explain above 500
- [x] #2 Backend 400 for long text says what is wrong (e.g. 'Text must be 1-500 characters.')
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #2 Tests pass
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Backend: ApiExceptionHandler gives a clear 400 detail for long or blank text ('Text must be 1-500 characters.'), with tests. 2. iOS: 500 limit in TranslationStore (UTF-16 count like Java), counter and 'Make it shorter.' in TranslateInputView, Explain disabled over the limit.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/26. Test it: type or paste over 500 characters. Expected: counter turns red 'Make it shorter. 538 / 500', Explain disabled. Backend 400 detail is 'Text must be 1-500 characters.' (restart backend to see it with curl). Automated: ./mvnw -B test (50 passed), TranslationControllerTest; TranslationStoreTests limit tests.

2026-10-12: merged into main with F2 (PR #29). Developer tested F2 on the simulator and approved; 119 iOS unit tests pass (iPhone 16 Pro, iOS 18.5); CI backend, pipeline and doc links pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
500-character counter in the app and a clear message on 400. Shipped with F2 (PR #29), developer-tested.
<!-- SECTION:FINAL_SUMMARY:END -->
