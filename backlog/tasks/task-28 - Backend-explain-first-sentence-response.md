---
id: TASK-28
title: 'Backend: explain-first sentence response'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-11 08:36'
updated_date: '2026-10-11 15:11'
labels:
  - backend
dependencies: []
priority: high
ordinal: 28000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
CLAUDE.md product rule: Saynata teaches English, it is not a translator. The main result for a sentence is a simple English explanation (B1-B2); the target-language text is a small helper line. The prompt and response model should ask the LLM for the English explanation first and keep the translation as a secondary field, and nothing should be named translate X. Cached rows made by the old prompt must not be served as if they were new.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The sentence prompt asks for the simple English explanation first; the target-language text is a secondary field
- [ ] #2 The response JSON puts the explanation first and the target-language text after the words
- [ ] #3 The sentence prompt version is bumped so old cached rows are not reused
- [ ] #4 Backend tests cover the new response shape; the iOS BackendClient decodes it
- [ ] #5 doc-4 (Sentence translation) and doc-1 glossary updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Prompt: ask for the English explanation first, target-language text is a secondary helper field. 2. Pin JSON order (meaning, words, translation) with @JsonPropertyOrder; keep field names (already plain, no iOS layout/decoding change). 3. Bump sentence prompt version v4 -> v5 in application.properties and iOS PromptVersions.sentence; update CacheTests hash. 4. Backend tests for the response shape. 5. Update doc-4 and doc-1.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Prompt now says explain first (1 meaning, 2 words, 3 translation as a helper). JSON order pinned with @JsonPropertyOrder. Field names kept (meaning/words/translation): the shape was already explain-first, so no iOS decoding or layout change. Sentence prompt v4 -> v5 in application.properties, iOS PromptVersions.sentence, and CacheTests hashes. doc-4 and doc-1 updated.

PR: https://github.com/tranphuocloc070699/saynata/pull/24 . Test it. Goal: explain-first sentence; non-English gives a clear 422. Steps: run backend + app; (1) 'I was reluctant to leave.' (2) 'asdkj qwe zzxq' (3) code line (4) Vietnamese sentence. Expected: (1) meaning, words, translation last, no wrong base form; (2-4) 'This doesn't look like English. Type an English word or sentence. (status 422)', nothing cached. Automated: cd backend && ./mvnw -B test; iOS xcodebuild test -only-testing:EnglishTranslationTests (both pass).
<!-- SECTION:NOTES:END -->
