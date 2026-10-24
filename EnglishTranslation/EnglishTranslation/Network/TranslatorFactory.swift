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
        BackendClient(baseURL: try AppConfig.backendBaseURL(), cache: ResultCache.shared, auth: AccountStore.shared)
    }

    /// The client for the language lines of a word card (TASK-106.3). Same backend, same sign-in.
    static func makeLineTranslator() throws -> any LineTranslating {
        BackendClient(baseURL: try AppConfig.backendBaseURL(), cache: ResultCache.shared, auth: AccountStore.shared)
    }

    /// One cheap request, so the first lookup reuses an open connection (TASK-83.5). Does nothing without `BackendBaseURL`.
    static func warmUpConnection() async {
        guard let url = try? AppConfig.backendBaseURL() else { return }
        await BackendClient(baseURL: url).warmUp()
    }

    /// The bundled dictionary file. Opened once; nil if it cannot be opened.
    private static let bundledDictionary = try? DictionaryStore.bundled()

    /// "Did you mean" for the bundled dictionary. Reads the word list only when it is first needed.
    private static let spellingSuggester = bundledDictionary.map { dictionary in
        SpellingSuggester(
            loadCandidates: { (try? dictionary.spellingCandidates()) ?? [] },
            checker: AppleSpellChecker())
    }

    /// Word lookup: bundled dictionary, then local cache, then our backend. Throws if `BackendBaseURL` is missing.
    static func makeWordLookup() throws -> any WordLooking {
        WordLookup(
            dictionary: bundledDictionary,
            backend: BackendClient(baseURL: try AppConfig.backendBaseURL(), cache: ResultCache.shared, auth: AccountStore.shared),
            suggester: spellingSuggester)
    }
}
