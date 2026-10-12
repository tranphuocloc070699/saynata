---
id: TASK-18
title: 'iOS: remove direct LLM clients and the provider picker'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-10 06:36'
updated_date: '2026-10-11 15:06'
labels:
  - ios
dependencies: []
priority: high
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The app can call DeepSeek, OpenAI and Claude directly with keys from Secrets.xcconfig. That skips the backend cache (spec R2), keeps API keys in the app bundle, and keeps a second copy of the prompt in Swift. The app should only talk to our backend.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 LLMClient, ClaudeClient, ChatCompleting, TranslationPrompt and Provider are removed
- [ ] #2 The provider picker is gone; the screen only has the language picker
- [ ] #3 TranslatorFactory / AppConfig only build BackendClient; API key entries removed from Info.plist and Secrets.xcconfig
- [ ] #4 Tests updated and passing
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #2 Tests pass
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Delete LLMClient, ClaudeClient, Provider, ChatCompleting, TranslationPrompt, Explanation.decode(fromModelReply). 2. Factory/AppConfig/Store build only BackendClient. 3. Remove picker and plist keys; empty Secrets.xcconfig stays (project references it). 4. Update tests and docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR for TASK-18. Test it: open the app, no provider picker, type a sentence -> explanation from backend; type a word -> word card; stop backend -> error text. Tests: 81 passed (EnglishTranslationTests). Secrets.xcconfig is now comment-only but must exist (project references it; build fails without it).
<!-- SECTION:NOTES:END -->
