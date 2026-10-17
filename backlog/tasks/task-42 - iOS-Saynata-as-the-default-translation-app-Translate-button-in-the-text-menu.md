---
id: TASK-42
title: >-
  iOS: Saynata as the default translation app (Translate button in the text
  menu)
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-11 15:39'
updated_date: '2026-10-17 11:29'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Move ShareSheetView to shared SelectedTextResultView (Open button optional). 2. New TranslationExtension target (ExtensionKit, point com.apple.public.translation-ui-provider), min iOS 18.4, same synchronized-folder sharing as ShareExtension. 3. Entitlement com.apple.developer.translation-app on app; network-access key in app and extension Info.plist. 4. Verify on iPhone 16 Pro iOS 18.5 simulator. 5. Update doc-10, CLAUDE.md; write signing steps in PR.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/27. Test it. Goal: Translate on selected text opens Saynata. Steps: run EnglishTranslation scheme on iPhone 16 Pro iOS 18.5; Settings > Apps > Default Apps > Translation (Saynata listed); Safari, long-press a word, > arrow, Translate, Continue. Expected: consent sheet, then word card; sentence gives sentence result; >500 chars gives limit message. Checked on simulator: listed and opens. Real iPhone and signing (restricted capability, may need paid Team) not verified; steps in PR. Automated: 104 tests pass, no new logic.

2026-10-14 phone test: Saynata does NOT appear in Settings > Apps > Default Apps > Translation. Likely cause: the com.apple.developer.translation-app entitlement was removed for the Personal Team build (local stash 'Personal Team entitlements'); a free Personal Team cannot sign this restricted capability. Blocked until a paid Apple Developer Program account (and, if Apple requires it, approval of the capability). Also needs iOS 18.4+.

2026-10-14: the developer parked this ('leave TASK-42 for later') until a paid Apple Developer account.

2026-10-17: paid account enrolled. PAID_ACCOUNT=1 scripts/install-on-phone.sh failed at signing: Xcode has no Apple account signed in ('No Accounts'), and the profile 'iOS Team Provisioning Profile: com.luke.EnglishTranslation' (team SVPCB3XQCD) lacks App Groups and Default Translation App. Code is unchanged and ready (app has com.apple.developer.translation-app + App Group; extension has App Group and the network-access key). Next: developer signs in to Xcode with the paid Apple ID, picks the paid Team on EnglishTranslation, ShareExtension, TranslationExtension, then reruns PAID_ACCOUNT=1 scripts/install-on-phone.sh.

2026-10-17 phone test (developer): Settings > Default Apps > Translation lists Saynata, and Translate on selected text in Books opens the Saynata sheet (AC #3 seen). It showed 'Sign in to get a new explanation' because the extensions had their own Keychain; fixed in PR #106 (App Group Keychain group, reload before refresh). Waiting for the developer's retest on PR #106.
<!-- SECTION:NOTES:END -->
