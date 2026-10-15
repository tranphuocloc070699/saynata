---
id: TASK-69
title: 'Backend: rate limits (IP, email, user)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 09:49'
updated_date: '2026-10-15 09:49'
labels:
  - backend
  - security
dependencies:
  - TASK-66
ordinal: 70000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Protects the email sender and the Claude budget from abuse. In-memory token buckets are enough for one server. Decision of 2026-10-15 (doc-7).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 email-code: about 5 per hour per IP and 3 per 15 minutes per email; new Claude calls: 60 per hour and 300 per day per user; public reads: 120 per minute per IP
- [ ] #2 Over the limit gives 429 with a Retry-After header and code rate_limited
- [ ] #3 Client IP is read like the admin login limit (Caddy X-Forwarded-For through Tomcat remoteip)
- [ ] #4 All numbers are in application.properties and can be overridden from env
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Small in-memory token bucket RateLimiter (clock injected). 2. Rules in app.limits.*. 3. Apply to email-code, new Claude calls, and a filter for public reads. 4. 429 + Retry-After + rate_limited. 5. Tests.
<!-- SECTION:PLAN:END -->
