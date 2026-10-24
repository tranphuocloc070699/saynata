//
//  TranslationCache.swift
//  EnglishTranslation
//

import Foundation

/// Keeps language lines in memory, so reopening a row is instant (TASK-40, TASK-106.3).
/// One entry per (English text, language). It holds the lines from the backend only; Apple Translation results
/// are not saved here, so a backend line can still replace them. It is gone when the app quits.
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

    /// The fallback when the backend has no line: Vietnamese uses the dictionary text, every other language is
    /// translated on the device.
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

    /// Whether the card shows a last row for this language: every language except English, when the sense has
    /// something to translate (the backend line is made from the simple explanation).
    func hasTargetLanguageRow(for language: TargetLanguage) -> Bool {
        guard language.hasTargetLanguageRow else { return false }
        return !simpleDefinition.isEmpty || dictionaryText(for: language) != nil
    }
}
