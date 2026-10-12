//
//  TranslatorFactory.swift
//  EnglishTranslation
//

import Foundation

/// Builds the real clients. They all talk to our Java backend.
enum TranslatorFactory {
    /// The sentence translator: our Java backend.
    static func makeTranslator() throws -> any Translating {
        try makeBackendClient()
    }

    /// Our Java backend. The URL comes from `BackendBaseURL` in Info.plist.
    static func makeBackendClient() throws -> any Translating {
        BackendClient(baseURL: try AppConfig.backendBaseURL(), cache: ResultCache.shared)
    }

    /// The bundled dictionary file. Opened once; nil if it cannot be opened.
    private static let bundledDictionary = try? DictionaryStore.bundled()

    /// "Did you mean" for the bundled dictionary. Reads the word list only when it is first needed.
    private static let spellingSuggester = bundledDictionary.map { dictionary in
        SpellingSuggester(loadCandidates: { (try? dictionary.spellingCandidates()) ?? [] })
    }

    /// Word lookup: bundled dictionary, then local cache, then our backend. Throws if `BackendBaseURL` is missing.
    static func makeWordLookup() throws -> any WordLooking {
        WordLookup(
            dictionary: bundledDictionary,
            backend: BackendClient(baseURL: try AppConfig.backendBaseURL(), cache: ResultCache.shared),
            suggester: spellingSuggester)
    }
}
