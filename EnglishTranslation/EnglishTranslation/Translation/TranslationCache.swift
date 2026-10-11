//
//  TranslationCache.swift
//  EnglishTranslation
//

import Foundation

/// Keeps on-device translations in memory, so reopening a row is instant (TASK-40).
/// One entry per (English text, language). It is gone when the app quits.
@MainActor
final class TranslationCache {
    static let shared = TranslationCache()

    private struct Key: Hashable {
        let text: String
        let language: TargetLanguage
    }

    private var translations: [Key: String] = [:]

    func translation(of text: String, in language: TargetLanguage) -> String? {
        translations[Key(text: text, language: language)]
    }

    func save(_ translation: String, of text: String, in language: TargetLanguage) {
        translations[Key(text: text, language: language)] = translation
    }
}

extension TargetLanguage {
    /// The language code Apple's Translation framework expects.
    /// Our code "zh" means Simplified Chinese, which Apple calls "zh-Hans".
    var appleIdentifier: String {
        self == .chinese ? "zh-Hans" : code
    }

    /// Vietnamese comes from the dictionary. Every other language is translated on the device.
    var needsOnDeviceTranslation: Bool { self != .vietnamese }

    /// English is the language of the explanation itself, so it gets no extra row.
    var hasTargetLanguageRow: Bool { self != .english }
}

extension Sense {
    /// The text for the target-language row when no translation is needed.
    /// Only Vietnamese has dictionary text; other languages return nil.
    func dictionaryText(for language: TargetLanguage) -> String? {
        guard language == .vietnamese, !vietnamese.isEmpty else { return nil }
        return vietnamese
    }

    /// Whether the card shows a last row for this language.
    func hasTargetLanguageRow(for language: TargetLanguage) -> Bool {
        guard language.hasTargetLanguageRow else { return false }
        return language.needsOnDeviceTranslation ? !simpleDefinition.isEmpty : !vietnamese.isEmpty
    }
}
