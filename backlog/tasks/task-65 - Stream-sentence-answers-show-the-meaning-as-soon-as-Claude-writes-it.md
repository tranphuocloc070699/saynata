---
id: TASK-65
title: 'Stream sentence answers: show the meaning as soon as Claude writes it'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 08:58'
updated_date: '2026-10-15 09:10'
labels:
  - backend
  - ios
dependencies: []
references:
  - backlog/docs/doc-16 - Plan-Mac-menu-bar-app-and-browser-extension.md
documentation:
  - backlog/docs/features/doc-4 - Sentence-translation.md
priority: high
ordinal: 66000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A new sentence takes 1.9-2.7 s (once 8.9 s) and the app shows nothing until the whole answer arrives. All of that time is the one Claude Haiku call. Developer decision 2026-10-15 (doc-16 Speed note, fix 1): stream the answer so the simple English meaning shows as soon as it is written. Old app versions keep using POST /api/translations.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 POST /api/translations/stream returns text/event-stream: a cache hit sends one done event; a miss sends meaning, then done (same TranslationResponse JSON as the old endpoint); a failure sends an error event
- [ ] #2 Not-English text gives the same outcome as today (nothing cached) as an error event; the final answer is validated and cached exactly as today
- [ ] #3 A small incremental parser pulls the completed meaning string out of the streamed JSON (escapes and unicode handled); unit tested
- [ ] #4 SSE is not buffered: Cache-Control no-cache and X-Accel-Buffering no are set
- [ ] #5 iOS: BackendClient streams with URLSession.bytes and an SSE line parser; TranslationStore goes loading, meaning, loaded; only the final result is saved in ResultCache
- [ ] #6 iOS: if streaming fails before done, the old endpoint is tried once, then the existing error shows
- [ ] #7 Unit tests: backend (event order, cache hit, not English, error, parser) and iOS (SSE chunks, store states, fallback)
- [ ] #8 doc-4 has the new sequence diagram; CLAUDE.md API list and doc-13 updated if needed
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Backend: JsonStringFieldReader (incremental meaning parser) + tests.
2. ClaudeService.explainSentenceStreaming (SDK createStreaming + MessageAccumulator; same params as today, prompt unchanged).
3. TranslationService.translateStreaming shares cache/personal-text/save logic with translate().
4. TranslationController POST /stream with SseEmitter, no-cache headers; events meaning/done/error. Tests with a fake streaming Claude.
5. iOS: SSEParser, BackendClient.explainStreaming, Translating default method, store state .meaning, fallback once on non-final errors; tiny view change (MeaningCard optional translation, preview view with ProgressView).
6. Unit tests, docs (doc-4 diagram, CLAUDE.md, doc-13 check), timing script, PR.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decision: not-English on the stream is an error event with status 422 (same message as the old 422), not a done event, because there is no TranslationResponse for it. The app does not fall back for 4xx answers.

Implemented backend SSE, iOS streaming and docs. Tests: backend 210 pass, iOS 179 pass (iPhone 16 Pro, iOS 18.5). No backend/.env in the worktree, so no real Claude timings: run BASE_URL=http://localhost:8081 python3 backend/scripts/measure_stream.py. Test it: new sentence in the app shows the Meaning card + spinner first, then words and translation row; same sentence again shows at once; 'asdkj qwe zzxq' shows the 422 message; backend off shows the normal error after one fallback try. Also: curl -N -H 'Content-Type: application/json' -H 'Accept: text/event-stream' -d '{"text":"...","targetLang":"vi"}' http://localhost:8081/api/translations/stream
<!-- SECTION:NOTES:END -->
