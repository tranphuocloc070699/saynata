---
id: TASK-4
title: 'iOS: bundle dictionary.sqlite and look up words'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-10 04:31'
updated_date: '2026-10-11 09:26'
labels:
  - ios
milestone: m-0
dependencies:
  - TASK-3
priority: high
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Words should be answered offline from the bundled dictionary first (spec R1, R2 step 1). Normalise rules must match backend CacheKeys.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 dictionary.sqlite is bundled in the app
- [ ] #2 A single-token input is normalised and looked up in the bundle, including word forms
- [ ] #3 Unit tests cover hit and miss
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Copy dictionary.sqlite into app Resources. 2. Add Dictionary/ files: WordEntry (+Sense, Usage), WordNormalizer (same as CacheKeys.normalizeWord), DictionaryStore (system SQLite3, lemma then forms). 3. Tests for hit, miss, form, normalise. 4. Update doc-5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Test it: run DictionaryStoreTests (hit run, form ran, miss zzzzqq). Command: xcodebuild test -project EnglishTranslation/EnglishTranslation.xcodeproj -scheme EnglishTranslation -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -only-testing:EnglishTranslationTests. Refresh bundle: cp pipeline/data/out/dictionary.sqlite EnglishTranslation/EnglishTranslation/Resources/. PR link in PR list for f1/task-4.
<!-- SECTION:NOTES:END -->
