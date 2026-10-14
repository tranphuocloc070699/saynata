---
id: TASK-53
title: 'AI images for senses marked in the admin (provider trial, budget cap)'
status: To Do
assignee: []
created_date: '2026-10-14 14:44'
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
Decision (doc-7, 2026-10-14, images option B): AI images only for senses the developer marks 'needs_ai' in the admin (expected ~150-300). Step 1 TRIAL: 20 marked or sample senses x 2 providers (for example Flux 2 klein ~$0.015/image and GPT Image 2 low quality), shown on a comparison page; the developer picks the provider and style (cost under $1; needs their API key; never run without it). Step 2 BUILD: a generator behind a small interface (ImageGenerator) so the provider can change; Claude Haiku writes a short image prompt from the word, its simple explanation and the easy example; a fixed style rule is added to every prompt: flat simple illustration, one clear subject, plain light background, Sage and Teal colours, no text or letters in the picture, no realistic faces. Output resized to 320 px WebP, saved as a media row with source=ai, provider, model, prompt, cost. Runs from the admin ('Generate with AI' on the needs_ai list) as a background job, with a hard budget cap per run (ADMIN sets it, default $5) and a running total; or as  for the bundled words. The admin shows an 'AI' label and keeps Regenerate / No image. Record provider terms (commercial use) in doc-11. Tests with a fake generator; never call a paid API in tests.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Trial page with 20 senses x 2 providers shown to the developer; provider and style chosen and recorded in doc-7
- [ ] #2 Generating for the needs_ai list stops at the budget cap and reports the cost; dry-run shows the estimate without spending
- [ ] #3 Each AI image is a 320 px WebP media row with source=ai, provider, model, prompt and cost; the admin shows an AI label
- [ ] #4 Tests use a fake generator; API keys come from .env and are documented in doc-11 and .env.example
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
