//
//  TranslationStoreTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// A translator that returns a fixed answer, or throws, with no network.
private struct FakeTranslator: Translating {
    var result: Result<Explanation, Error>

    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        try result.get()
    }
}

/// A small explanation for tests.
private func sample(_ translation: String) -> Explanation {
    Explanation(meaning: "A greeting.", words: [], translation: translation)
}

/// A word lookup that returns a fixed entry, or throws, and remembers the word it got.
private final class FakeWordLookup: WordLooking, @unchecked Sendable {
    var result: Result<WordEntry, Error>
    var lastWord: String?
    /// What `suggestions(for:)` returns, and the word it was asked about.
    var suggestionWords: [String] = []
    var suggestionsAskedFor: String?

    init(_ result: Result<WordEntry, Error>) {
        self.result = result
    }

    func suggestions(for word: String) -> [String] {
        suggestionsAskedFor = word
        return suggestionWords
    }

    func lookUp(_ word: String) async throws -> WordEntry {
        lastWord = word
        return try result.get()
    }
}

@MainActor
struct TranslationStoreTests {
    @Test func emptyInputCannotTranslate() {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("x"))) })
        store.inputText = "   \n"
        #expect(store.canTranslate == false)
    }

    // MARK: - 500-character limit (TASK-22)

    @Test func exactly500CharactersIsAllowed() {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("x"))) })
        store.inputText = String(repeating: "a", count: 500)
        #expect(store.inputLength == 500)
        #expect(store.isOverLimit == false)
        #expect(store.canTranslate == true)
    }

    @Test func oneCharacterOverTheLimitCannotTranslate() {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("x"))) })
        store.inputText = String(repeating: "a", count: 501)
        #expect(store.isOverLimit == true)
        #expect(store.canTranslate == false)
    }

    @Test func spacesAroundTheTextAreNotCounted() {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("x"))) })
        store.inputText = "  " + String(repeating: "a", count: 500) + "\n "
        #expect(store.inputLength == 500)
        #expect(store.canTranslate == true)
    }

    @Test func anEmojiCountsAsTwoLikeJava() {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("x"))) })
        store.inputText = String(repeating: "😀", count: 250)
        #expect(store.inputLength == 500)
        store.inputText = String(repeating: "😀", count: 251)
        #expect(store.isOverLimit == true)
    }

    @Test func overTheLimitNothingIsSent() async {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("x"))) })
        store.inputText = String(repeating: "word ", count: 120)
        await store.translate()
        guard case .idle = store.state else {
            Issue.record("Expected .idle, got \(store.state)")
            return
        }
    }

    @Test func successShowsLoadedText() async {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("Xin chào"))) })
        store.inputText = "Hello there"
        await store.translate()
        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(result.explanation.translation == "Xin chào")
        #expect(result.sentence == "Hello there")
    }

    // MARK: - The result keeps its language (TASK-14)

    @Test func loadedResultKeepsItsLanguageWhenThePickerChanges() async {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("こんにちは"))) })
        store.targetLanguage = .japanese
        store.inputText = "Hello there"
        await store.translate()

        store.targetLanguage = .french
        store.inputText = "Something else"

        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(result.language == .japanese)
        #expect(result.sentence == "Hello there")
    }

    @Test func pickerChangedWhileWaitingDoesNotChangeTheResultLanguage() async {
        /// Changes the picker while the "request" is in flight.
        struct PickerChangingTranslator: Translating {
            let store: TranslationStore
            func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
                await MainActor.run { store.targetLanguage = .french }
                return sample("こんにちは")
            }
        }
        var store: TranslationStore!
        store = TranslationStore(makeTranslator: { PickerChangingTranslator(store: store) })
        store.targetLanguage = .japanese
        store.inputText = "Hello there"
        await store.translate()

        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(result.language == .japanese)
    }

    @Test func translatorErrorShowsFailedMessage() async {
        let store = TranslationStore(makeTranslator: {
            FakeTranslator(result: .failure(BackendError.badStatus(401, detail: nil)))
        })
        store.inputText = "Hello there"
        await store.translate()
        guard case let .failed(message, _) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(message.contains("401"))
    }

    @Test func missingKeyShowsFailedMessage() async {
        let store = TranslationStore(makeTranslator: { throw AppConfig.ConfigError.missing("BackendBaseURL") })
        store.inputText = "Hello there"
        await store.translate()
        guard case let .failed(message, _) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(message.contains("BackendBaseURL"))
    }

    @Test func passesSelectedLanguage() async {
        let recorder = LanguageRecorder()
        let store = TranslationStore(makeTranslator: { recorder })
        store.targetLanguage = .japanese
        store.inputText = "Hello there"
        await store.translate()
        #expect(await recorder.lastLanguage == .japanese)
    }

    @Test func defaultsToVietnamese() {
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(sample("x"))) })
        #expect(store.targetLanguage == .vietnamese)
    }

    // MARK: - One word or a sentence (TASK-19)

    @Test func oneWordGoesToTheWordLookupNotTheSentenceTranslator() async {
        let lookup = FakeWordLookup(.success(.run))
        let store = TranslationStore(
            makeTranslator: { Issue.record("A word must not use the sentence translator"); throw BackendError.badStatus(0, detail: nil) },
            makeWordLookup: { lookup })
        store.inputText = "  Ran \n"
        await store.translate()

        guard case let .loadedWord(entry) = store.state else {
            Issue.record("Expected .loadedWord, got \(store.state)")
            return
        }
        #expect(entry == .run)
        #expect(lookup.lastWord == "Ran")
    }

    @Test func moreThanOneWordGoesToTheSentenceTranslator() async {
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .success(sample("Xin chào"))) },
            makeWordLookup: { Issue.record("A sentence must not use the word lookup"); throw WordLookupError.notOneWord })
        store.inputText = "Hello   there"
        await store.translate()

        guard case .loaded = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
    }

    @Test func wordLookupErrorShowsFailedMessage() async {
        let store = TranslationStore(makeWordLookup: { FakeWordLookup(.failure(WordLookupError.notAnEnglishWord("asdkj"))) })
        store.inputText = "asdkj"
        await store.translate()

        guard case let .failed(message, _) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(message == "\"asdkj\" is not an English word. Check the spelling.")
    }

    @Test func notAnEnglishWordCarriesSuggestions() async {
        let fake = FakeWordLookup(.failure(WordLookupError.notAnEnglishWord("runj")))
        fake.suggestionWords = ["run", "rung"]
        let store = TranslationStore(makeWordLookup: { fake })
        store.inputText = " Runj! "
        await store.translate()

        guard case let .failed(message, spelling) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(message == "\"runj\" is not an English word. Check the spelling.")  // the old message stays
        #expect(spelling == SpellingHelp(typedWord: "runj", suggestions: ["run", "rung"]))
        #expect(fake.suggestionsAskedFor == "runj")
    }

    @Test func offlineLookupAlsoCarriesSuggestions() async {
        let fake = FakeWordLookup(.failure(URLError(.notConnectedToInternet)))
        fake.suggestionWords = ["run"]
        let store = TranslationStore(makeWordLookup: { fake })
        store.inputText = "runj"
        await store.translate()

        guard case let .failed(_, spelling) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(spelling?.suggestions == ["run"])
    }

    @Test func otherErrorsHaveNoSuggestions() async {
        let fake = FakeWordLookup(.failure(BackendError.badStatus(502, detail: "down")))
        fake.suggestionWords = ["run"]
        let store = TranslationStore(makeWordLookup: { fake })
        store.inputText = "serendipity"
        await store.translate()

        guard case let .failed(_, spelling) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(spelling == nil)
    }

    @Test func tappingASuggestionLooksItUp() async {
        let fake = FakeWordLookup(.success(WordEntry(word: "run", ipa: nil, senses: [])))
        let store = TranslationStore(makeWordLookup: { fake })
        store.inputText = "runj"
        await store.lookUp(suggestion: "run")

        #expect(store.inputText == "run")
        #expect(fake.lastWord == "run")
        guard case .loadedWord = store.state else {
            Issue.record("Expected .loadedWord, got \(store.state)")
            return
        }
    }

    @Test func sendsTrimmedText() async {
        let recorder = LanguageRecorder()
        let store = TranslationStore(makeTranslator: { recorder })
        store.inputText = "  Hello there \n"
        await store.translate()
        #expect(await recorder.lastText == "Hello there")
    }
}

/// Remembers the text and language it was asked for.
private actor LanguageRecorder: Translating {
    var lastText: String?
    var lastLanguage: TargetLanguage?

    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        lastText = text
        lastLanguage = language
        return sample("x")
    }
}
