---
id: TASK-71
title: 'Backend: send sign-in emails with Resend (SES stays optional)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 10:50'
updated_date: '2026-10-15 10:55'
labels:
  - backend
dependencies: []
ordinal: 71000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer decision 2026-10-15: use Resend for sign-in code emails instead of AWS SES (SES needs sandbox exit, IAM user and region setup). Add ResendEmailSender, choose sender at start-up (Resend, then SES, then fake log), docs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ResendEmailSender posts to api.resend.com/emails with Bearer key, retries once on 429/5xx, never throws to caller, never logs key or code
- [ ] #2 Sender choice: RESEND_API_KEY, else SES, else fake log; no key = same behaviour as today
- [ ] #3 RESEND_API_KEY and EMAIL_FROM passed in docker-compose.prod.yml and .env.example
- [ ] #4 doc-11 Resend setup section before SES; doc-17, doc-7, CLAUDE.md updated
- [ ] #5 Backend tests and check_docs pass
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. ResendEmailSender (JDK HttpClient, 10s timeout, one retry). 2. AuthConfig picks Resend over SES over fake; EMAIL_FROM with SES_FROM_EMAIL fallback. 3. Tests with a fake HTTP server. 4. Compose, .env.example, docs, doc-7 row. 5. PR into main.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/65. Implemented ResendEmailSender, sender choice in AuthConfig, tests (285 pass), docs. Test it: Goal: sign-in emails go out with Resend. Steps: follow doc-11 section 10 (Resend setup), set RESEND_API_KEY and EMAIL_FROM=login@loffy.me in the server .env, deploy, curl POST /api/auth/email-code, then Gmail Show original. Expected: 204, mail arrives, DKIM/SPF/DMARC pass; with a wrong key still 204 and the log shows HTTP 401, never the key or code. Without RESEND_API_KEY: fake log sender as before. Automated: ResendEmailSenderTest, EmailSenderConfigTest; cd backend && ./mvnw -B test.
<!-- SECTION:NOTES:END -->
