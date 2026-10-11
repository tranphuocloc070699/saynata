---
id: TASK-42
title: >-
  iOS: Saynata as the default translation app (Translate button in the text
  menu)
status: To Do
assignee: []
created_date: '2026-10-11 15:39'
labels:
  - ios
dependencies:
  - TASK-30
priority: high
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer request 2026-10-11: tapping Translate on selected text in any app should show Saynata, like DeepL does. Since iOS 18.4 a person can pick a default translation app in Settings > Apps > Default Apps > Translation; the app needs the Translation capability (com.apple.developer.translation-app), a TranslationUIProviderExtension target, and the translation-ui-provider network-access Info.plist key. Docs: https://developer.apple.com/tutorials/data/documentation/translationuiprovider/preparing-your-app-to-be-the-default-translation-app.md . Region and signing limits may apply (some reports: US and EU only; a paid developer account may be needed for the capability).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A Translation extension target shows the Saynata sheet (word card for one word, sentence cards for a sentence) for TranslationUIProviderContext.inputText, reusing the share-extension views
- [ ] #2 Capability, entitlement and network-access key are set; exact Xcode signing steps are documented for the developer
- [ ] #3 Tested on the developer's iPhone: Settings > Default Apps > Translation lists Saynata, and Translate on selected text in Safari opens it
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
