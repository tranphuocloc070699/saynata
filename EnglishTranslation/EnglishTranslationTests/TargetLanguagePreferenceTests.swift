//
//  TargetLanguagePreferenceTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// TASK-109: the picked target language is saved and restored. Each test uses its own empty defaults.
@MainActor
struct TargetLanguagePreferenceTests {

    private let defaults = TargetLanguagePreference.makeEmptyDefaults()

    private var preference: TargetLanguagePreference { TargetLanguagePreference(defaults: defaults) }

    @Test func nothingSavedReadsAsVietnamese() {
        #expect(preference.language == .vietnamese)
    }

    @Test func savedLanguageComesBack() {
        preference.language = .korean
        #expect(preference.language == .korean)
        #expect(defaults.string(forKey: TargetLanguagePreference.key) == "ko")
    }

    @Test func savedThaiReadsAsVietnamese() {
        defaults.set("th", forKey: TargetLanguagePreference.key)
        #expect(preference.language == .vietnamese)
    }

    @Test func savedUnknownCodeReadsAsVietnamese() {
        defaults.set("xx", forKey: TargetLanguagePreference.key)
        #expect(preference.language == .vietnamese)
    }

    @Test func pickingInTheStoreSavesIt() {
        let store = TranslationStore(languagePreference: preference)
        store.targetLanguage = .japanese
        #expect(preference.language == .japanese)
    }

    @Test func aNewStoreStartsWithTheSavedLanguage() {
        preference.language = .german
        let store = TranslationStore(languagePreference: preference)
        #expect(store.targetLanguage == .german)
    }

    @Test func aNewStoreWithSavedThaiStartsWithVietnamese() {
        defaults.set("th", forKey: TargetLanguagePreference.key)
        let store = TranslationStore(languagePreference: preference)
        #expect(store.targetLanguage == .vietnamese)
    }
}
