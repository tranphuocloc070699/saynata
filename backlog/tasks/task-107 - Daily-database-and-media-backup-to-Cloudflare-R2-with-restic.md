---
id: TASK-107
title: Daily database and media backup to Cloudflare R2 with restic
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-24 09:52'
updated_date: '2026-10-24 09:58'
labels: []
dependencies: []
priority: high
ordinal: 119000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
There is no backup today. The server Postgres holds the source of truth for the dictionary, every cached LLM answer, users and liked items, and MEDIA_DIR holds the pictures and clips. Losing the VPS would lose all of it. Developer choice (2026-10-24): 3-2-1 rule; main off-server copy on Cloudflare R2 (10 GB free, no download fees) through restic; the last 3 dumps stay on the VPS for fast restore; a monthly copy on the developer Mac. The agent writes scripts and docs only; the developer creates the R2 bucket and keys and installs the timer.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 backend/scripts/backup.sh makes a pg_dump -Fc of the Docker Postgres, keeps the last 3 dumps on the VPS, and backs up the dump and MEDIA_DIR to a restic repository on R2 with retention 7 daily, 4 weekly, 6 monthly
- [ ] #2 backend/scripts/restore.sh restores a chosen snapshot into a scratch database and prints row counts, so a monthly restore test is one command
- [ ] #3 A systemd timer (or cron line) runs it every night; an optional HEALTHCHECK_URL is pinged on success and on failure
- [ ] #4 New keys (RESTIC_REPOSITORY, RESTIC_PASSWORD, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, HEALTHCHECK_URL) are in .env.example and doc-11 has a backup section with setup, restore and the monthly test; scripts pass shellcheck
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. backup.sh (pg_dump via docker compose exec, keep 3, restic to R2, retention, health pings). 2. restore.sh into scratch postgres:17 container with row counts. 3. systemd service+timer, cron line in doc. 4. backup-copy-to-mac.sh (restic copy). 5. .env.example keys, doc-11 section 16. 6. shellcheck.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Scripts and docs written; shellcheck passes. R2_ACCESS_KEY_ID/R2_SECRET_ACCESS_KEY are used instead of AWS_* in .env because AWS_ACCESS_KEY_ID is already the SES key and compose passes it to the backend; backup.sh maps them for restic.
<!-- SECTION:NOTES:END -->
