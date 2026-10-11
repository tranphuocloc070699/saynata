//
//  WordLookupTests.swift
//  EnglishTranslationTests
//

import Foundation
import SQLite3
import Testing
@testable import EnglishTranslation

/// A backend stand-in. Counts calls and returns a fixed entry, or throws.
private final class FakeBackend: WordFetching, @unchecked Sendable {
    var result: Result<WordEntry, Error>
    var calls: [String] = []

    init(_ result: Result<WordEntry, Error>) {
        self.result = result
    }

    func entry(forWord word: String) async throws -> WordEntry {
        calls.append(word)
        return try result.get()
    }
}

private let backendEntry = WordEntry(
    word: "serendipity", ipa: nil,
    senses: [Sense(pos: "noun", cefr: "C2", simpleDefinition: "a lucky thing found by chance",
                   vietnamese: "sự tình cờ may mắn", examples: ["Pure serendipity."])])

/// Builds a tiny dictionary file with the word "run" and the form "ran", like the real one.
private func makeDictionary() throws -> DictionaryStore {
    let url = URL.temporaryDirectory.appending(path: "lookup test \(UUID().uuidString).sqlite")
    var handle: OpaquePointer?
    #expect(sqlite3_open(url.path(percentEncoded: false), &handle) == SQLITE_OK)
    defer { sqlite3_close(handle) }
    let sql = """
        CREATE TABLE words (id INTEGER PRIMARY KEY, lemma TEXT, ipa TEXT);
        CREATE TABLE forms (word_id INTEGER, form TEXT);
        CREATE TABLE senses (word_id INTEGER, position INTEGER, pos TEXT, cefr TEXT,
                             simple_definition TEXT, vietnamese TEXT, examples TEXT);
        INSERT INTO words VALUES (1, 'run', '/ɹʌn/');
        INSERT INTO forms VALUES (1, 'ran');
        INSERT INTO senses VALUES (1, 0, 'verb', 'A1', 'move fast on your feet', 'chạy', '["I run."]');
        """
    #expect(sqlite3_exec(handle, sql, nil, nil, nil) == SQLITE_OK)
    return try DictionaryStore(url: url)
}

struct WordLookupTests {
    @Test func bundledWordNeverReachesTheBackend() async throws {
        let backend = FakeBackend(.success(backendEntry))
        let lookup = WordLookup(dictionary: try makeDictionary(), backend: backend)

        let entry = try await lookup.lookUp("ran")

        #expect(entry.word == "run")
        #expect(backend.calls.isEmpty)
    }

    @Test func wordMissingFromTheBundleGoesToTheBackend() async throws {
        let backend = FakeBackend(.success(backendEntry))
        let lookup = WordLookup(dictionary: try makeDictionary(), backend: backend)

        let entry = try await lookup.lookUp("Serendipity")

        #expect(entry == backendEntry)
        #expect(backend.calls == ["Serendipity"])
    }

    @Test func missingDictionaryFileStillUsesTheBackend() async throws {
        let backend = FakeBackend(.success(backendEntry))
        let lookup = WordLookup(dictionary: nil, backend: backend)

        #expect(try await lookup.lookUp("serendipity") == backendEntry)
    }

    @Test func emptySensesMeansNotAnEnglishWord() async throws {
        let backend = FakeBackend(.success(WordEntry(word: "asdkj", ipa: nil, senses: [])))
        let lookup = WordLookup(dictionary: try makeDictionary(), backend: backend)

        await #expect(throws: WordLookupError.notAnEnglishWord("asdkj")) {
            try await lookup.lookUp(" ASDKJ ")
        }
        #expect(WordLookupError.notAnEnglishWord("asdkj").localizedDescription
            == "\"asdkj\" is not an English word. Check the spelling.")
    }

    @Test func punctuationIsRemovedBeforeEveryStep() async throws {
        let backend = FakeBackend(.success(backendEntry))
        let lookup = WordLookup(dictionary: try makeDictionary(), backend: backend)

        // Bundle: "Ran!" finds "run".
        #expect(try await lookup.lookUp("Ran!").word == "run")
        // Backend: it gets the word without punctuation, so "Run!" and "run" share one cache entry.
        _ = try await lookup.lookUp("\u{201C}Serendipity.\u{201D}")
        #expect(backend.calls == ["Serendipity"])
        #expect(CacheKeys.wordKey("Serendipity") == CacheKeys.wordKey("serendipity"))
    }

    @Test func onlyPunctuationGivesTheNotOneWordMessage() async throws {
        let backend = FakeBackend(.success(backendEntry))
        let lookup = WordLookup(dictionary: try makeDictionary(), backend: backend)

        await #expect(throws: WordLookupError.notOneWord) {
            try await lookup.lookUp("?!")
        }
        #expect(backend.calls.isEmpty)
    }

    @Test func badRequestGivesAShortMessage() async throws {
        let backend = FakeBackend(.failure(BackendError.badStatus(400, detail: "Validation failure")))
        let lookup = WordLookup(dictionary: nil, backend: backend)

        await #expect(throws: WordLookupError.notOneWord) {
            try await lookup.lookUp("hello!")
        }
    }

    @Test func otherErrorsReachTheUserAsTheyAre() async {
        let refused = FakeBackend(.failure(BackendError.badStatus(422, detail: "Claude declined to answer this text.")))
        await #expect(throws: BackendError.badStatus(422, detail: "Claude declined to answer this text.")) {
            try await WordLookup(dictionary: nil, backend: refused).lookUp("serendipity")
        }

        let offline = FakeBackend(.failure(URLError(.notConnectedToInternet)))
        await #expect(throws: URLError.self) {
            try await WordLookup(dictionary: nil, backend: offline).lookUp("serendipity")
        }
    }
}
