---
id: TASK-94
title: Explain text when word lookup has no entry
status: In Progress
assignee: []
created_date: '2026-10-22 09:20'
updated_date: '2026-10-22 09:36'
labels: []
dependencies: []
documentation:
  - backlog/docs/features/doc-5 - Word-lookup.md
type: bug
ordinal: 100000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A learner entered well-looked and saw a final not-an-English-word error. The word allowlist rejects unlisted compounds, including some valid ones. The learner should get the best available explanation without needing to know the app routing rules.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A word lookup with no senses retries the existing sentence explanation flow and shows its result when available.
- [ ] #2 Bundled and cached words still show word cards; unexplainable text still shows an error.
- [ ] #3 Main app and Quick Explain use the same fallback behavior, with focused tests.
- [ ] #4 Word lookup feature documentation describes the fallback and current line references.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add focused failing tests for unknown-word fallback. 2. Route no-senses word results through the existing sentence explanation in TranslationStore and QuickExplainer. 3. Run focused tests and update doc-5 through Backlog CLI.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented a text-explanation fallback for WordLookupError.notAnEnglishWord in TranslationStore and QuickExplainer. Known words still use word cards. On a failed text explanation, the main app keeps spelling suggestions. Added focused tests for successful and failed fallback. iPhone 16 Pro (iOS 18.5): 50 TranslationStoreTests and QuickExplainerTests passed; docs checker: 24 docs, 0 problems. No live Claude request or device UI check yet, so keep In Progress.
<!-- SECTION:NOTES:END -->
