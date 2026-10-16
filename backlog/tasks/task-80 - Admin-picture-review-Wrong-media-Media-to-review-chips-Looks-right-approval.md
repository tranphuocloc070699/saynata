---
id: TASK-80
title: >-
  Admin: picture review (Wrong media / Media to review chips, Looks right
  approval)
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-16 02:33'
updated_date: '2026-10-16 02:40'
labels:
  - backend
  - admin
  - media
dependencies: []
ordinal: 79000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The admin Words list 'Needs review' chip only shows word-text problems. The Claude media check (TASK-78) flagged 743 bundled pictures as wrong and --apply set media_status = needs_ai on them, but nothing in the admin shows them. The developer also wants to review every sense that has a picture or clip and approve the good ones. Decision 2026-10-16: picture review is separate from the word-text Needs review. Approval is stored apart from media_status (TASK-53 uses needs_ai): media_reviewed_at and media_reviewed_media_id, so approval belongs to one media file.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Flyway V14 adds media_reviewed_at and media_reviewed_media_id to dictionary_sense and word_lookup_media
- [ ] #2 GET /api/admin/words accepts media=wrong and media=to-review (plus with/without); the total matches the filter
- [ ] #3 AdminWord has wrongMediaCount, mediaToReviewCount and per sense mediaReview (approved, to_review, wrong, none) with the latest media_check verdict and reason
- [ ] #4 POST and DELETE /api/admin/words/{id}/senses/{position}/media/approve set and undo the approval; approve clears needs_ai; ADMIN only
- [ ] #5 A new picture for a sense makes it to_review again
- [ ] #6 Admin UI: Wrong media and Media to review chips, list badges, a Looks right button with Approved label and undo, Claude verdict text
- [ ] #7 Backend and admin tests pass; doc-7, doc-13, doc-14, doc-15 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. V15 migration (V14 is used by PR #72). 2. SenseReview + SenseMediaReader review queries; AdminWord counts and mediaReview. 3. media=wrong|to-review SQL filters. 4. approve/unapprove endpoints (approve clears needs_ai). 5. Admin chips, badges, check button, verdict text. 6. Tests, docs 7/13/14/15.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built. Backend 334 tests pass; admin 73 tests pass, build ok; check_docs ok. Migration is V15; must merge after PR #72 (V14) or be renumbered.

PR: https://github.com/tranphuocloc070699/saynata/pull/73 (merge after #72, migration V15). Test it: on admin.loffy.me press the Wrong media chip (about 743 words), open a word, press the check button (Approved label, AI mark gone), press it again to undo, then press Media to review; after Pick another an approved sense is to review again. Automated: ./mvnw -B test (334), npm test (73) and npm run build, python3 scripts/check_docs.py.
<!-- SECTION:NOTES:END -->
