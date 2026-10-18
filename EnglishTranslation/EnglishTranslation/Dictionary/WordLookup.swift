//
//  WordLookup.swift
//  EnglishTranslation
//

import Foundation

/// Anything that can find a dictionary entry for one word. The real one is `WordLookup`; tests use a fake.
protocol WordLooking: Sendable {
    /// Returns the entry for `word`. Throws `WordLookupError`, `BackendError` or a `URLError`.
    func lookUp(_ word: String) async throws -> WordEntry

    /// Close bundled words for a word that was not found ("did you mean"). Works offline.
    func suggestions(for word: String) -> [String]

    /// The entry from the bundled dictionary only: no cache, no network. Nil when the word is not there.
    /// A sentence result uses it for the form pill and level chip on each word card.
    func bundledEntry(for word: String) -> WordEntry?

    /// The word card for a phrase ("give up"), only when the phone cache or the server cache has one (TASK-83.10).
    /// Nil on a miss or any error: the caller then explains the text as a sentence. Never causes a Claude call.
    func phraseEntry(for phrase: String) async -> WordEntry?
}

extension WordLooking {
    /// Most lookups have no suggestions (tests use this default).
    func suggestions(for word: String) -> [String] { [] }

    /// Tests use this default: nothing is in the bundle.
    func bundledEntry(for word: String) -> WordEntry? { nil }

    /// Tests use this default: no phrase is cached.
    func phraseEntry(for phrase: String) async -> WordEntry? { nil }
}

/// The second step of a word lookup: the local cache and our backend. `BackendClient` is the real one.
protocol WordFetching: Sendable {
    /// Returns the entry from the local cache, or asks the backend and saves the answer.
    /// An entry with no senses means the backend says it is not an English word.
    func entry(forWord word: String) async throws -> WordEntry

    /// The cached entry for a phrase: the local cache, then `GET /api/words/{phrase}?cachedOnly=true`.
    /// Nil when the server says 404 `not_cached`.
    func cachedEntry(forPhrase phrase: String) async throws -> WordEntry?
}

extension WordFetching {
    /// Tests use this default: no phrase is cached.
    func cachedEntry(forPhrase phrase: String) async throws -> WordEntry? { nil }
}

/// Why a word has no entry. The message is shown to the user as it is.
enum WordLookupError: LocalizedError, Equatable {
    case notAnEnglishWord(String)
    case notOneWord

    var errorDescription: String? {
        switch self {
        case let .notAnEnglishWord(word): "\"\(word)\" is not an English word. Check the spelling."
        case .notOneWord: "Type one English word, with letters only."
        }
    }
}

/// The full lookup order (spec R2): bundled dictionary, then local cache, then the backend.
struct WordLookup: WordLooking {
    /// The dictionary inside the app. Nil if it cannot be opened (then every word goes to the backend).
    let dictionary: DictionaryStore?
    let backend: any WordFetching
    /// Finds "did you mean" words in the bundle. Nil if there is no dictionary.
    var suggester: SpellingSuggester?

    func suggestions(for word: String) -> [String] {
        suggester?.suggestions(for: CacheKeys.trimmingWordPunctuation(word)) ?? []
    }

    func bundledEntry(for word: String) -> WordEntry? {
        let cleaned = CacheKeys.trimmingWordPunctuation(word)
        guard !cleaned.isEmpty else { return nil }
        return try? dictionary?.lookUp(cleaned)
    }

    func phraseEntry(for phrase: String) async -> WordEntry? {
        guard let entry = try? await backend.cachedEntry(forPhrase: phrase), !entry.senses.isEmpty else { return nil }
        return entry
    }

    func lookUp(_ input: String) async throws -> WordEntry {
        // "Run!" and "run" must be the same word, for the bundle, the cache key and the backend.
        let word = CacheKeys.trimmingWordPunctuation(input)
        guard !word.isEmpty else { throw WordLookupError.notOneWord }

        // 1. The bundle. A broken file is treated like a miss.
        if let entry = try? dictionary?.lookUp(word) {
            return entry
        }

        // 2. The local cache, then 3. the backend (both inside `backend`).
        let entry: WordEntry
        do {
            entry = try await backend.entry(forWord: word)
        } catch BackendError.badStatus(400, _) {
            throw WordLookupError.notOneWord
        }
        guard !entry.senses.isEmpty else {
            throw WordLookupError.notAnEnglishWord(CacheKeys.normalizeWord(word))
        }
        return entry
    }
}
