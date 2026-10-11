//
//  WordLookup.swift
//  EnglishTranslation
//

import Foundation

/// Anything that can find a dictionary entry for one word. The real one is `WordLookup`; tests use a fake.
protocol WordLooking: Sendable {
    /// Returns the entry for `word`. Throws `WordLookupError`, `BackendError` or a `URLError`.
    func lookUp(_ word: String) async throws -> WordEntry
}

/// The second step of a word lookup: the local cache and our backend. `BackendClient` is the real one.
protocol WordFetching: Sendable {
    /// Returns the entry from the local cache, or asks the backend and saves the answer.
    /// An entry with no senses means the backend says it is not an English word.
    func entry(forWord word: String) async throws -> WordEntry
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
