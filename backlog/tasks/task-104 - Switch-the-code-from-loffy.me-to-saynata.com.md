---
id: TASK-104
title: Switch the code from loffy.me to saynata.com
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-24 06:03'
updated_date: '2026-10-24 06:13'
labels: []
dependencies: []
ordinal: 111000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer bought saynata.com (Cloudflare, 2026-10-18). DNS and Caddy already serve api, admin and the website on both domains. The code still points to loffy.me, so new app, extension and admin builds must use saynata.com while loffy.me stays as an alias for installed apps and old links.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 App, Mac app, extensions, admin, extension, web and pipeline defaults use saynata.com
- [ ] #2 Backend allows both admin origins and the admin cookie works on the new origin
- [ ] #3 Extension host_permissions keep both domains for one release
- [ ] #4 deploy.yml health checks use saynata.com
- [ ] #5 Docs (doc-11, doc-23, doc-17, CLAUDE.md, doc-7) say saynata.com and the Test it card lists the manual steps
- [ ] #6 All test suites pass
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Replace the old domain with the new one in code, config and tests (keep both in extension host_permissions and backend admin origins). 2. Check media URL storage. 3. Update docs. 4. Run all tests.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done in code; PR into feature/local. Test it: developer verifies saynata.com in Resend then sets EMAIL_FROM, PUBLIC_BASE_URL, MEDIA_BASE_URL in VPS backend/.env; release + deploy; build new app and extension. Media URLs are built at read time (file names in Postgres, media/<file> in dictionary.sqlite), no data rewrite. Both domains kept in admin CORS and extension host_permissions.
<!-- SECTION:NOTES:END -->
