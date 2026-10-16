---
id: TASK-83.4
title: 'Backend: cached-only lookups, cache headers and prefix suggestions'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 08:19'
updated_date: '2026-10-16 08:54'
labels: []
dependencies:
  - TASK-83.1
parent_task_id: TASK-83
priority: medium
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
So the app can ask for answers while the user types without ever causing a Claude call, and repeat lookups need no network.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/words/{word}?cachedOnly=true and the sentence endpoints with cachedOnly return a cached or bundled answer or 404 not_cached, never call Claude and never go through ClaudeGate
- [ ] #2 Cached and bundled word answers send Cache-Control (public, max-age agreed in the task plan)
- [ ] #3 GET /api/suggest?q=<prefix> returns up to 8 bundled words, cached words and cached phrases that start with the prefix, in under 50 ms on the server, rate limited like other public reads
- [ ] #4 Tests and doc-5 / doc-4 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. NotCachedException (404 not_cached, Cache-Control: no-store). WordService.lookUp(word, cachedOnly) returns the answer plus where it came from (bundled, cached, new, not a word); with cachedOnly a miss throws before ClaudeGate. TranslationService: the askClaude step throws not_cached when cachedOnly, so the gate and Claude are never reached (personal text too). POST /api/translations and /stream take ?cachedOnly=true; the stream sends an error event with code not_cached.
2. Cache-Control on GET /api/words/{word}: bundled answers 'public, max-age=86400' (1 day: bundled entries change only by a rare admin edit or a deploy); cached backend words 'public, max-age=3600' (1 hour: the Pixabay picture and admin review edits arrive after the first save, so a shorter window). New Claude answers and 'not a word' answers get no header (the picture is still missing; the word list changes on deploy). Same values with and without cachedOnly. Repeat lookups in a session need no network (URLCache honours max-age).
3. GET /api/suggest?q=<prefix>&lang=<optional>: up to 8, words first (exact match first, bundled by NGSL rank, then cached backend words of the current prompt version by length), then cached phrases (sentence_translation of the current prompt version, optional target language filter) by length; words keep at most 5 places when phrases exist. Prefix lower-cased, LIKE wildcards escaped, max 100 chars; blank q gives an empty list. Cache-Control public, max-age=300. Added to PublicReadRateLimitFilter.
4. V19 Java migration: text_pattern_ops prefix indexes on Postgres (dictionary_word.lemma, word_lookup.word, lower(sentence_translation.source_text)); plain indexes on H2. Each source query has LIMIT, so it stays far under 50 ms.
5. Tests with a mocked ClaudeService (MockMvc): cachedOnly hit/miss for words and both sentence endpoints, guest with REQUIRE_SIGN_IN=true gets cached answers and 404 (not 401), Claude never called; Cache-Control values; suggest order, limit, escaping, rate limit. Update doc-4, doc-5, CLAUDE.md overview; check_docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built on prefill/task-83.4 (from feature/prefill, PR #81 still open). cachedOnly: WordService.lookUp(word, cachedOnly) returns WordAnswer (response + origin); TranslationService throws NotCachedException in the askClaude step before the gate. Cache-Control as planned; new and not-a-word answers keep Spring Security's default no-cache, no-store. /api/suggest in suggest/ with JdbcClient. Changed from the plan: cached words and phrases are NOT sorted in SQL (ORDER BY length read every match: 89 ms on 200k phrases with prefix 'p' on local Postgres 15; plain LIMIT under 1 ms, Java sorts the 24 rows). V19 checked on a local Postgres 15 (indexes used, both new test classes pass on Postgres). Backend tests: 375 pass. Docs: doc-4, doc-5, doc-17, CLAUDE.md overview.
<!-- SECTION:NOTES:END -->
