---
id: TASK-89.3
title: 'Short sentence summary: gist line, up to 3 bullets, bold key words (prompt v9)'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-23 08:38'
updated_date: '2026-10-23 08:57'
labels:
  - backend
  - ios
  - extension
dependencies: []
parent_task_id: TASK-89
priority: high
ordinal: 103000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer found the sentence summary too long: prompt v8 told the model to keep about the same length as the original, so a 58-word comment came back as 58 words (production check 2026-10-23: 84% of v8 summaries are longer than the original). The learner wants only what the text says, in simple words and short sentences, with a list and bold to show the ideas. Decided 2026-10-23: v9 as tested on 4 real deepseek calls, keep bold, answer every cached and prefilled sentence again with v9, try temperature 0. Apple research and measurements: shared notes short-summary-proposal.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Prompt v9: a short text (one sentence up to 15 words) gets one plain sentence; a longer text gets one gist line plus up to 3 '- ' bullets, at most half the words of the original, B1 words, 1-3 **bold** key words
- [ ] #2 The backend keeps only the agreed markdown (lines starting with '- ', max 3, and **bold**) and sends temperature 0 when configured; tests use the fake Claude
- [ ] #3 sentence-prompt-version is v9 and an admin call puts every done sentence prefill item back to pending, so the night job answers them again with v9
- [ ] #4 iOS and Mac show bullets and bold in the summary without the markers; tapping a word, the part popup, Listen, Liked and History use the plain text
- [ ] #5 The browser extension shows bullets and bold without innerHTML
- [ ] #6 doc-4 and doc-7 (and doc-18, doc-19 where touched) are updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Backend: prompt v9 in SENTENCE_PROMPT, SummaryFormat cleans the summary to the Markdown subset, app.claude.temperature (CLAUDE_TEMPERATURE) on sentence calls, sentence-prompt-version v9, Flyway V26 requeues done sentence prefill items.
2. iOS/Mac: SummaryMarkup parses the subset; SentenceTextView/SelectableSentenceText show bullets (hanging indent) and bold without markers; Listen, Liked, History use plain text; quick explain snippet uses the attributed string; PromptVersions.sentence v9.
3. Extension: shared/summary.ts + summaryNodes in card.ts (DOM, no innerHTML).
4. Tests (backend, iOS, extension), docs (doc-4, doc-7, doc-11, doc-18, doc-19, CLAUDE.md), PR into feature/local.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Real deepseek-v4.1-flash test calls (4 + 2, about $0.01): long texts shrink to about half, short lines unchanged. temperature 0 is accepted by deepseek through STEYG, but two calls with 0 still gave slightly different summaries (thinking model), so stability comes from the cache.
Flyway: V24 and V25 are Java migrations (src/main/java/db/migration), so the requeue is V26.
The server needs CLAUDE_TEMPERATURE=0 in backend/.env (default empty = not sent, because Claude Haiku refuses it).

PR: https://github.com/tranphuocloc070699/saynata/pull/151 (into feature/local). Tests: backend 514, iOS 437, extension 124 pass.
<!-- SECTION:NOTES:END -->
