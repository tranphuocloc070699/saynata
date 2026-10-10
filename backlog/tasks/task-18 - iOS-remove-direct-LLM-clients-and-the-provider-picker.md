---
id: TASK-18
title: 'iOS: remove direct LLM clients and the provider picker'
status: To Do
assignee: []
created_date: '2026-10-10 06:36'
updated_date: '2026-10-10 08:25'
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
