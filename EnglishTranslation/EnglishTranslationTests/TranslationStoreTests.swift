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

    /// Words that are "in the bundle", for the sentence word cards.
    var bundled: [String: WordEntry] = [:]

    func bundledEntry(for word: String) -> WordEntry? {
        bundled[word]
    }

    func lookUp(_ word: String) async throws -> WordEntry {
        lastWord = word
        return try result.get()
    }

    /// Phrases "in the cache", and the phrase it was asked about.
    var phrases: [String: WordEntry] = [:]
    var phraseAskedFor: String?

    func phraseEntry(for phrase: String) async -> WordEntry? {
        phraseAskedFor = phrase
        return phrases[phrase]
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

    // MARK: - Tapped words in a sentence result (TASK-41, TASK-89.2)

    @Test func theSummaryIsTheMeaningOfTheResult() async {
        let explanation = Explanation(meaning: "The fog hid the hills.", words: [], translation: "y")
        let store = TranslationStore(makeTranslator: { FakeTranslator(result: .success(explanation)) })
        store.inputText = "The mist blurred the hills"
        await store.translate()

        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(result.explanation.meaning == "The fog hid the hills.")
    }

    // MARK: - Phrases (TASK-83.10)

    @Test func aCachedPhraseShowsItsWordCardWithoutTheSentencePath() async {
        let giveUp = WordEntry(word: "give up", ipa: nil, senses: [
            Sense(pos: "phrasal verb", cefr: "A2", simpleDefinition: "to stop trying", vietnamese: "bỏ cuộc", examples: [])])
        let lookup = FakeWordLookup(.failure(WordLookupError.notOneWord))
        lookup.phrases = ["Give up": giveUp]
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .failure(BackendError.streamEnded)) },
            makeWordLookup: { lookup })
        store.inputText = "Give up!"
        await store.translate()

        #expect(lookup.phraseAskedFor == "Give up")
        guard case let .loadedWord(entry) = store.state else {
            Issue.record("Expected .loadedWord, got \(store.state)")
            return
        }
        #expect(entry == giveUp)
    }

    @Test func aPhraseTheCacheDoesNotKnowIsExplainedAsASentence() async {
        let lookup = FakeWordLookup(.failure(WordLookupError.notOneWord))
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .success(sample("x"))) },
            makeWordLookup: { lookup })
        store.inputText = "How are you"
        await store.translate()

        #expect(lookup.phraseAskedFor == "How are you")
        guard case .loaded = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
    }

    @Test func sentenceStillLoadsWhenThereIsNoWordLookup() async {
        let explanation = Explanation(meaning: "x", words: [], translation: "y")
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .success(explanation)) },
            makeWordLookup: { throw AppConfig.ConfigError.missing("BackendBaseURL") })
        store.inputText = "The edges of it"
        await store.translate()

        guard case .loaded = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
    }

    @Test func aTappedWordFromTheSentenceIsLookedUpByItsLowercaseForm() async throws {
        let lookup = FakeWordLookup(.success(.run))
        let store = TranslationStore(makeWordLookup: { lookup })
        let token = try #require(SentenceTokenizer.tokens(in: "The Blurred edges").dropFirst().first)
        let entry = try await store.wordEntry(for: SentenceWordInfo(tappedWord: token.lookupWord))
        #expect(entry == .run)
        #expect(lookup.lastWord == "blurred")
    }

    @Test func tappedWordUsesTheBundleEntryWithoutALookup() async throws {
        let lookup = FakeWordLookup(.failure(WordLookupError.notOneWord))
        let store = TranslationStore(makeWordLookup: { lookup })
        let info = SentenceWordInfo(word: .init(word: "blurred", partOfSpeech: "verb", meaning: "x"), entry: .depend)
        let entry = try await store.wordEntry(for: info)
        #expect(entry == .depend)
        #expect(lookup.lastWord == nil)
    }

    @Test func tappedWordNotInTheBundleIsLookedUp() async throws {
        let lookup = FakeWordLookup(.success(.run))
        let store = TranslationStore(makeWordLookup: { lookup })
        let info = SentenceWordInfo(word: .init(word: "edges", partOfSpeech: "noun", meaning: "x"), entry: nil)
        let entry = try await store.wordEntry(for: info)
        #expect(entry == .run)
        #expect(lookup.lastWord == "edges")
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

    @Test func articleAndOneWordLooksUpTheBareWord() async {
        for (input, expected) in [("an apple", "apple"), ("The apple!", "apple"), ("  A  Apple. ", "Apple")] {
            let lookup = FakeWordLookup(.success(.run))
            let store = TranslationStore(
                makeTranslator: { Issue.record("An article and a word must not use the sentence translator"); throw BackendError.badStatus(0, detail: nil) },
                makeWordLookup: { lookup })
            store.inputText = input
            await store.translate()

            guard case .loadedWord = store.state else {
                Issue.record("Expected .loadedWord for \(input), got \(store.state)")
                continue
            }
            #expect(lookup.lastWord == expected, "input: \(input)")
        }
    }

    @Test func articleAndTwoWordsStaysASentence() async {
        let lookup = FakeWordLookup(.failure(WordLookupError.notOneWord))
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .success(sample("Quả táo đỏ"))) },
            makeWordLookup: { lookup })
        store.inputText = "the red apple"
        await store.translate()
        #expect(lookup.lastWord == nil)

        guard case .loaded = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
    }

    @Test func anArticleAloneIsStillLookedUpAsAWord() async {
        let lookup = FakeWordLookup(.success(.run))
        let store = TranslationStore(makeWordLookup: { lookup })
        store.inputText = "the"
        await store.translate()
        #expect(lookup.lastWord == "the")
    }

    @Test func spellingHelpUsesTheWordWithoutTheArticle() async {
        let fake = FakeWordLookup(.failure(WordLookupError.notAnEnglishWord("aple")))
        fake.suggestionWords = ["apple"]
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .failure(BackendError.badStatus(422, detail: "Not English"))) },
            makeWordLookup: { fake })
        store.inputText = "an Aple!"
        await store.translate()
        #expect(fake.suggestionsAskedFor == "aple")
    }

    @Test func moreThanOneWordGoesToTheSentenceTranslator() async {
        // The sentence path may read the bundle (for word cards) but must never call `lookUp`.
        let lookup = FakeWordLookup(.failure(WordLookupError.notOneWord))
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .success(sample("Xin chào"))) },
            makeWordLookup: { lookup })
        store.inputText = "Hello   there"
        await store.translate()
        #expect(lookup.lastWord == nil)

        guard case .loaded = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
    }

    @Test func unknownWordFallsBackToTheSentenceExplanation() async {
        let lookup = FakeWordLookup(.failure(WordLookupError.notAnEnglishWord("well-looked")))
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .success(sample("được chăm sóc tốt"))) },
            makeWordLookup: { lookup })
        store.inputText = "well-looked"
        await store.translate()

        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(lookup.lastWord == "well-looked")
        #expect(result.sentence == "well-looked")
        #expect(result.explanation.translation == "được chăm sóc tốt")
    }

    @Test func unknownWordStillShowsAnErrorWhenTheSentenceServiceRejectsIt() async {
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .failure(BackendError.badStatus(422, detail: "Not English"))) },
            makeWordLookup: { FakeWordLookup(.failure(WordLookupError.notAnEnglishWord("asdkj"))) })
        store.inputText = "asdkj"
        await store.translate()

        guard case .failed = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
    }

    @Test func notAnEnglishWordCarriesSuggestions() async {
        let fake = FakeWordLookup(.failure(WordLookupError.notAnEnglishWord("runj")))
        fake.suggestionWords = ["run", "rung"]
        let store = TranslationStore(
            makeTranslator: { FakeTranslator(result: .failure(BackendError.badStatus(422, detail: "Not English"))) },
            makeWordLookup: { fake })
        store.inputText = " Runj! "
        await store.translate()

        guard case let .failed(message, spelling) = store.state else {
            Issue.record("Expected .failed, got \(store.state)")
            return
        }
        #expect(message == "Not English (status 422)")
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
