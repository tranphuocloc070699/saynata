---
id: TASK-102.2
title: Deploy the web app to loffy.me
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-24 03:36'
updated_date: '2026-10-24 03:39'
labels:
  - web
dependencies: []
parent_task_id: TASK-102
ordinal: 110000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The learner web app (web/, TASK-102.1) only runs on the developer's Mac. loffy.me gives a Cloudflare 522 because nothing is behind it. The existing auto deploy (deploy.yml) ships the backend and the admin app; the web app must ship the same way, without running next build on the small VPS.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 web/ builds a Next.js standalone server that runs in a node:22-alpine container (web service in docker-compose.prod.yml, 127.0.0.1:3001)
- [ ] #2 deploy.yml has a deploy-web job after deploy: builds in GitHub Actions, copies the build to the VPS, restarts only the web container, then checks https://loffy.me with a timeout
- [ ] #3 Caddy lines for loffy.me and www.loffy.me (/api/* to the backend, the rest to the web app) and the DNS record are written in doc-11
- [ ] #4 doc-23 Deploy section, doc-11 web section, doc-7 decision row and the CLAUDE.md web/ row are updated; check_docs.py passes
- [ ] #5 cd web && npm test && npm run build pass
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. next.config: output standalone. 2. web/Dockerfile copies a prebuilt standalone folder (no build on the VPS). 3. web service in docker-compose.prod.yml on 127.0.0.1:3001; deploy.sh starts only postgres+backend. 4. web/scripts/deploy-web.sh builds the small image and restarts web. 5. deploy.yml job deploy-web: npm ci, build, rsync to web/dist, run script, curl check. 6. Docs: doc-11, doc-23, doc-7, CLAUDE.md. 7. npm test, npm run build, docker-free checks, check_docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built: standalone web container, deploy-web job, doc-11 section 14, doc-23, doc-7, CLAUDE.md. Test it: (1) Cloudflare A loffy.me + www -> 110.172.29.29 DNS only; (2) add the loffy.me and www Caddy blocks from doc-11 section 14 and reload caddy; (3) merge to feature/local, release PR to main (deploy-web runs); (4) open https://loffy.me, explain 'give up' and a sentence. Automated: cd web && npm test && npm run build. PR: see below.
<!-- SECTION:NOTES:END -->
