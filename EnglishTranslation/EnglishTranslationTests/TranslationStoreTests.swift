//
//  TranslationStoreTests.swift
//  EnglishTranslationTests
//

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

    init(_ result: Result<WordEntry, Error>) {
        self.result = result
    }

    func lookUp(_ word: String) async throws -> WordEntry {
        lastWord = word
        return try result.get()
    }
}

@MainActor
struct TranslationStoreTests {
    @Test func emptyInputCannotTranslate() {
        let store = TranslationStore(makeTranslator: { _ in FakeTranslator(result: .success(sample("x"))) })
        store.inputText = "   \n"
        #expect(store.canTranslate == false)
    }

    @Test func successShowsLoadedText() async {
        let store = TranslationStore(makeTranslator: { _ in FakeTranslator(result: .success(sample("Xin chào"))) })
        store.inputText = "Hello there"
        await store.translate()
        guard case let .loaded(explanation) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(explanation.translation == "Xin chào")
    }

    @Test func translatorErrorShowsFailedMessage() async {
        let store = TranslationStore(makeTranslator: { _ in
            FakeTranslator(result: .failure(LLMError.badStatus(401)))
        })
        store.inputText = "Hello there"
        await store.translate()
        guard case let .failed(message) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(message.contains("401"))
    }

    @Test func missingKeyShowsFailedMessage() async {
        let store = TranslationStore(makeTranslator: { _ in throw AppConfig.ConfigError.missing("ClaudeAPIKey") })
        store.inputText = "Hello there"
        await store.translate()
        guard case let .failed(message) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(message.contains("ClaudeAPIKey"))
    }

    @Test func usesSelectedProvider() async {
        var requested: Provider?
        let store = TranslationStore(makeTranslator: { provider in
            requested = provider
            return FakeTranslator(result: .success(sample("x")))
        })
        store.provider = .claude
        store.inputText = "Hello there"
        await store.translate()
        #expect(requested == .claude)
    }

    @Test func passesSelectedLanguage() async {
        let recorder = LanguageRecorder()
        let store = TranslationStore(makeTranslator: { _ in recorder })
        store.targetLanguage = .japanese
        store.inputText = "Hello there"
        await store.translate()
        #expect(await recorder.lastLanguage == .japanese)
    }

    @Test func defaultsToServerAndVietnamese() {
        let store = TranslationStore(makeTranslator: { _ in FakeTranslator(result: .success(sample("x"))) })
        #expect(store.provider == .server)
        #expect(store.targetLanguage == .vietnamese)
    }

    // MARK: - One word or a sentence (TASK-19)

    @Test func oneWordGoesToTheWordLookupNotTheSentenceTranslator() async {
        let lookup = FakeWordLookup(.success(.run))
        let store = TranslationStore(
            makeTranslator: { _ in Issue.record("A word must not use the sentence translator"); throw LLMError.badStatus(0) },
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
            makeTranslator: { _ in FakeTranslator(result: .success(sample("Xin chào"))) },
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

        guard case let .failed(message) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(message == "\"asdkj\" is not an English word. Check the spelling.")
    }

    @Test func sendsTrimmedText() async {
        let recorder = LanguageRecorder()
        let store = TranslationStore(makeTranslator: { _ in recorder })
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

/// Decoding the model's JSON reply.
struct ExplanationDecodingTests {
    private let json = """
        {"meaning": "He took an old watch out.",         "words": [{"word": "drew out", "partOfSpeech": "phrasal verb", "meaning": "took out"}],         "translation": "Anh ấy rút ra một chiếc đồng hồ."}
        """

    @Test func decodesPlainJSON() throws {
        let explanation = try Explanation.decode(fromModelReply: json)
        #expect(explanation.words.first?.partOfSpeech == "phrasal verb")
        #expect(explanation.translation.hasPrefix("Anh ấy"))
    }

    @Test func decodesJSONInsideMarkdownFence() throws {
        let explanation = try Explanation.decode(fromModelReply: "Sure!\n```json\n\(json)\n```")
        #expect(explanation.meaning == "He took an old watch out.")
    }

    @Test func throwsBadFormatForPlainText() {
        #expect(throws: LLMError.self) {
            try Explanation.decode(fromModelReply: "Anh ấy rút ra một chiếc đồng hồ.")
        }
    }
}
