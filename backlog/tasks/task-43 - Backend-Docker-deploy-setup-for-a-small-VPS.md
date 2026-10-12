---
id: TASK-43
title: 'Backend: Docker deploy setup for a small VPS'
status: Done
assignee: []
created_date: '2026-10-11 16:25'
updated_date: '2026-10-12 02:15'
labels:
  - backend
  - devops
dependencies: []
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
To use the app on the developer's iPhone anywhere, the backend must run on a server instead of localhost. The developer has a ~$10 VPS budget. This prepares everything without deploying or spending money.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 backend/Dockerfile (multi-stage, Java 17) builds a small image; docker-compose.yml runs the backend + Postgres with env vars from a .env file that is never committed
- [x] #2 A healthcheck endpoint or Actuator health is used by compose
- [x] #3 A short deploy guide (doc page) explains: copy files to the VPS, set .env, docker compose up, open the port or put it behind HTTPS, and set the app's BackendBaseURL
- [x] #4 CI builds the Docker image on pull requests
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Merged in PR #32. CI builds the image (job 'Backend Docker image'). Not yet run end to end: Docker Desktop on the Mac was hung during the worker's run. To finish: follow the PR #32 Test it card once (prod compose on port 18081, project saynata-test), then mark Done. Deploy guide: backlog/docs/guides doc-11.

2026-10-12: end-to-end check passed after PR #33 (multi-arch base images; -alpine had no arm64): prod compose on port 18081, /actuator/health UP, /api/words/runk answered without Claude, /actuator/env 404, container healthy, image 430 MB.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Docker deploy setup for a small VPS: Dockerfile (multi-arch Temurin 17, non-root), docker-compose.prod.yml with Postgres and healthchecks, deploy guide doc-11, CI image build (PRs #32, #33). Verified end to end locally.
<!-- SECTION:FINAL_SUMMARY:END -->
