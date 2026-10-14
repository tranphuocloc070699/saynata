---
id: TASK-30
title: 'iOS: share extension to explain selected text'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-11 08:36'
updated_date: '2026-10-14 11:18'
labels:
  - ios
dependencies:
  - TASK-19
  - TASK-28
priority: low
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
From the UX report (docs/design/translate-ux-2026-10-10.md, item 11): selecting text in Safari or Messages and sharing it to Saynata takes about 3 taps with no typing and no app switch, which beats opening the app. The report says to do it after the core word and sentence flows are stable.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Saynata appears in the share sheet for selected text
- [x] #2 The result shows in a sheet, using the same word or sentence path as the app
- [x] #3 Text over the 500-character limit shows a clear message
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Shared text routing (TextRoute: empty / tooLong >500 / word / sentence) and DeepLink (saynata://lookup?text=) in the app folder, with unit tests.
2. App: register the saynata URL scheme, .onOpenURL fills the input and explains it. ResultCache moves to the App Group container (falls back to Application Support).
3. Extension target ShareExtension: reuse the app source folder through target membership (exclude app-only files), own folder with ShareViewController (UIHostingController), ShareSheetView, Info.plist (text activation rule, BackendBaseURL), entitlements.
4. WordCardView: split out WordHeaderView so the sheet can put Listen / Open in Saynata / Close beside the headword.
5. pbxproj: small hand edits, then clean build of both targets, run unit tests, drive Notes/Safari share sheet on iPhone 16 Pro.
6. Docs: new feature page Share extension, CLAUDE.md overview, doc-3 table.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-11: developer chose to build the share extension before images. Design approved from mockup: a sheet over the host app with the headword, IPA, icon-only Listen / Open in Saynata / Close, and the same meaning cards (compact). Branch feature/f6-share-extension, cut from feature/f1-word-lookup (F1 not merged to main yet).

PR: https://github.com/tranphuocloc070699/saynata/pull/25 (into feature/f6-share-extension). Test it: Goal: share selected text from Safari to Saynata and see a sheet. Steps: 1) run the app on iPhone 16 Pro (installs the extension); 2) Safari, long-press a word, menu > Share... > the EnglishTranslation tile; 3) check header (headword, IPA, Listen / Open in Saynata / Close) and cards; 4) share 2+ words (sentence), then 500+ characters. Expected: word card with closed Vietnamese row last; sentence view with quoted text header; over 500: 'This text has N characters. The limit is 500.' Open in Saynata opens the app on the same text. Failure case: backend off + word not in bundle gives the app's error and Did you mean. Automated: TextRouteTests (104 tests pass), xcodebuild test -project EnglishTranslation/EnglishTranslation.xcodeproj -scheme EnglishTranslation -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.5' -only-testing:EnglishTranslationTests -parallel-testing-enabled NO. Real device needs App Groups capability on both targets (steps in the PR).

Verified 2026-10-14: developer tested on a real iPhone 13 (iOS 18) with the main build after PR #37/#38 (word, sentence, speakers, share sheet, 'an apple', media all worked); iOS unit tests 146 passed (Codex run on the Mac, iPhone 16 Pro iOS 18.5 simulator); docs check 0 problems.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Share extension (Share, then Saynata) shows the explain sheet with the app's word/sentence path; merged in PR #30; Listen wired in PR #37; verified on the developer's iPhone.
<!-- SECTION:FINAL_SUMMARY:END -->
