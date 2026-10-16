---
id: TASK-53
title: 'AI images for senses marked in the admin (provider trial, budget cap)'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-14 14:44'
updated_date: '2026-10-16 02:27'
labels:
  - backend
  - pipeline
  - media
dependencies:
  - TASK-52
priority: medium
type: task
ordinal: 56000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decision (doc-7, 2026-10-14, images option B): AI images only for senses the developer marks 'needs_ai' in the admin (expected ~150-300). Step 1 TRIAL: 20 marked or sample senses x 2 providers (for example Flux 2 klein ~$0.015/image and GPT Image 2 low quality), shown on a comparison page; the developer picks the provider and style (cost under $1; needs their API key; never run without it). Step 2 BUILD: a generator behind a small interface (ImageGenerator) so the provider can change; Claude Haiku writes a short image prompt from the word, its simple explanation and the easy example; a fixed style rule is added to every prompt: flat simple illustration, one clear subject, plain light background, Sage and Teal colours, no text or letters in the picture, no realistic faces. Output resized to 320 px WebP, saved as a media row with source=ai, provider, model, prompt, cost. Runs from the admin ('Generate with AI' on the needs_ai list) as a background job, with a hard budget cap per run (ADMIN sets it, default $5) and a running total; or as the pipeline command "uv run pipeline imagegen --max-cost N --dry-run" for the bundled words. The admin shows an 'AI' label and keeps Regenerate / No image. Record provider terms (commercial use) in doc-11. Tests with a fake generator; never call a paid API in tests.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Trial page with 20 senses x 2 providers shown to the developer; provider and style chosen and recorded in doc-7
- [x] #2 Generating for the needs_ai list stops at the budget cap and reports the cost; dry-run shows the estimate without spending
- [ ] #3 Each AI image is a 320 px WebP media row with source=ai, provider, model, prompt and cost; the admin shows an AI label
- [x] #4 Tests use a fake generator; API keys come from .env and are documented in doc-11 and .env.example
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Developer chose OpenAI GPT Image mini only (2026-10-16), so the trial is 20 senses x 1 provider.
1. Flyway V14: media.source allows 'ai'; new table media_generation (sense, file, provider, model, prompt, tokens, cost, media_id once linked).
2. pipeline/src/pipeline/media_generate.py: ImageGenerator protocol + OpenAIImageGenerator (httpx, images/generations, low quality, 1536x1024), fixed style prompt from headword/pos/explanation/easy example (no extra LLM call), 320 px WebP via media.resize_and_save_webp, name <word>_<n>_ai_<8hex>.webp, budget cap that never passes --max-cost (default 5), --dry-run estimate, report data/out/media-generate.html (old vs new).
3. --link: after rsync, links only files that answer 200 at MEDIA_BASE_URL, link-media rules (skip none/removed, only still needs_ai), media row source=ai, media_status=ai_generated; old file kept.
4. CLI media-generate; tests with a fake generator and fake HTTP; Postgres tests behind TEST_DATABASE_URL.
5. Admin: credit line says AI image instead of 'on Pixabay' for source=ai.
6. Docs: doc-15, doc-6, doc-11 (OPENAI_API_KEY, terms), doc-7, doc-9, CLAUDE.md, .env.example.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-16 build (branch pipeline/media-generate):
- Provider: developer chose OpenAI GPT Image mini only, so AC#1 becomes 20 senses x 1 provider. Trial not run (agents never start paid runs): uv run pipeline media-generate --limit 20, about $0.08.
- Built: pipeline/src/pipeline/media_generate.py + CLI media-generate (--limit --words --provider --model --max-cost --dry-run --regenerate --link --workers). Flyway V14: media.source allows 'ai', new table media_generation (provider, model, prompt, tokens, cost, old_media_id, media_id once linked). --link links only files that answer 200 at MEDIA_BASE_URL, sets media_status ai_generated, old file kept.
- Prompt is a fixed template (no Claude call). Provider/model/prompt/cost are on media_generation, not media (join on media_id); media.license_note = 'AI image (openai gpt-image-1-mini)'.
- Admin: source=ai shows the license note instead of the Pixabay credit (text-only, same slot as 'Uploaded by the admin'). A separate AI badge and a 'Generate with AI' button need a mockup: follow-up.
- Verified: pipeline 154 passed with TEST_DATABASE_URL (Postgres 16 in Docker, V14 applied), backend ./mvnw -B test passed (H2, V14 applied), admin npm test 68 passed, check_docs 0 problems. CLI dry run and missing-key message checked against a seeded local Postgres.
- Not built: backend-word senses (word_lookup_media needs_ai), clips re-check with 3 frames (separate task).
<!-- SECTION:NOTES:END -->
