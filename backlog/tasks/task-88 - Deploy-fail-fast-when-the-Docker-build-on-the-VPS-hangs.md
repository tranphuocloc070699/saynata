---
id: TASK-88
title: 'Deploy: fail fast when the Docker build on the VPS hangs'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-17 12:53'
updated_date: '2026-10-17 12:54'
labels:
  - backend
  - deploy
dependencies: []
ordinal: 90000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
On 2026-10-17 (commit e6882ad) dockerd logged 'session healthcheck failed fatally' for the BuildKit session at the start of the deploy build on the VPS, and docker compose then waited forever. The GitHub Deploy job has no time limit, so it ran 15+ minutes until cancelled by hand, and the hung docker compose / buildx bake processes stayed on the server and had to be killed before a re-run worked. A normal deploy takes about 3 minutes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Both jobs in .github/workflows/deploy.yml have timeout-minutes
- [ ] #2 backend/scripts/deploy.sh kills a stuck build on the server with timeout, so no build processes linger
- [ ] #3 A build that fails or times out is retried once before the deploy fails
- [ ] #4 doc-11 says what to do when a deploy hangs (cancel, kill leftover processes, re-run)
- [ ] #5 What gets deployed does not change
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. deploy.sh: build the backend image alone (docker compose build), under timeout --kill-after (DEPLOY_BUILD_TIMEOUT, default 360 s); timeout signals the whole process group, so compose and buildx bake die together; then kill any leftover buildx bake of this user and retry once.
2. Then up -d --wait without --build (same image), also under timeout.
3. deploy.yml: timeout-minutes 20 (backend) and 10 (admin); ssh ServerAliveInterval so a dead link is noticed.
4. doc-11: section 7 text + a 'deploy hangs' troubleshooting row.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
deploy.sh: build backend alone under timeout --kill-after=30 (DEPLOY_BUILD_TIMEOUT, default 360 s), one retry after pkill of leftover buildx bake; up -d --wait without --build under timeout 360. Checked locally that timeout kills a grandchild process (exit 124). deploy.yml: timeout-minutes 20/10, ssh ServerAlive 30s x6. doc-11 section 7 + Troubleshooting row. Not run on the VPS (not allowed to deploy from this session).
<!-- SECTION:NOTES:END -->
