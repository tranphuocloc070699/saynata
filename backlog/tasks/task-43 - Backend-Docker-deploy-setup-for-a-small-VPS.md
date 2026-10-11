---
id: TASK-43
title: 'Backend: Docker deploy setup for a small VPS'
status: To Do
assignee: []
created_date: '2026-10-11 16:25'
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
- [ ] #1 backend/Dockerfile (multi-stage, Java 17) builds a small image; docker-compose.yml runs the backend + Postgres with env vars from a .env file that is never committed
- [ ] #2 A healthcheck endpoint or Actuator health is used by compose
- [ ] #3 A short deploy guide (doc page) explains: copy files to the VPS, set .env, docker compose up, open the port or put it behind HTTPS, and set the app's BackendBaseURL
- [ ] #4 CI builds the Docker image on pull requests
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
