//
//  TranslationRowPreferenceTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

struct TranslationRowPreferenceTests {

    /// A fresh, empty settings store for each test.
    private func makePreference() -> TranslationRowPreference {
        let suite = "TranslationRowPreferenceTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return TranslationRowPreference(defaults: defaults)
    }

    @Test func defaultIsCollapsed() {
        let preference = makePreference()
        #expect(preference.mode == .collapsed)
        #expect(!preference.startsOpen)
    }

    @Test func collapsedStaysClosedEvenAfterAnOpen() {
        let preference = makePreference()
        preference.rowChanged(isOpen: true)
        #expect(!preference.startsOpen)
    }

    @Test func alwaysOpenStartsOpenEvenAfterAClose() {
        let preference = makePreference()
        preference.mode = .alwaysOpen
        preference.rowChanged(isOpen: false)
        #expect(preference.startsOpen)
    }

    @Test func rememberLastStartsClosedAtFirst() {
        let preference = makePreference()
        preference.mode = .rememberLast
        #expect(!preference.startsOpen)
    }

    @Test func rememberLastFollowsTheLastChoice() {
        let preference = makePreference()
        preference.mode = .rememberLast
        preference.rowChanged(isOpen: true)
        #expect(preference.startsOpen)
        preference.rowChanged(isOpen: false)
        #expect(!preference.startsOpen)
    }

    @Test func modeIsSavedInTheStore() {
        let preference = makePreference()
        preference.mode = .alwaysOpen
        let again = TranslationRowPreference(defaults: preference.defaults)
        #expect(again.mode == .alwaysOpen)
    }

    @Test func unknownSavedValueFallsBackToCollapsed() {
        let preference = makePreference()
        preference.defaults.set("nonsense", forKey: TranslationRowPreference.modeKey)
        #expect(preference.mode == .collapsed)
    }
}
