//
//  TranslationStoreStreamingTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

private let fullAnswer = Explanation(
    meaning: "He did not want to leave.",
    words: [.init(word: "reluctant", partOfSpeech: "adjective", meaning: "not wanting to do something")],
    translation: "Anh ấy không muốn đi.")

/// A translator whose streaming call and plain call are both under the test's control.
private final class StreamingFake: Translating, @unchecked Sendable {
    typealias OnMeaning = @MainActor @Sendable (String) -> Void
    /// What `explainStreaming` does. It may call `onMeaning` any number of times.
    var stream: @Sendable (OnMeaning) async throws -> Explanation
    /// What the plain `explain` returns.
    var plain: Result<Explanation, Error> = .success(fullAnswer)
    private(set) var streamCalls = 0
    private(set) var plainCalls = 0

    init(stream: @escaping @Sendable (OnMeaning) async throws -> Explanation) {
        self.stream = stream
    }

    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        plainCalls += 1
        return try plain.get()
    }

    func explainStreaming(_ text: String, translatingTo language: TargetLanguage,
                          onMeaning: @escaping OnMeaning) async throws -> Explanation {
        streamCalls += 1
        return try await stream(onMeaning)
    }

    /// What the 4-argument call does when a test sets it. Otherwise it works like the 3-argument one.
    var streamWithDeltas: (@Sendable (_ onDelta: OnMeaning, _ onMeaning: OnMeaning) async throws -> Explanation)?

    func explainStreaming(_ text: String, translatingTo language: TargetLanguage,
                          onMeaningDelta: @escaping OnMeaning,
                          onMeaning: @escaping OnMeaning) async throws -> Explanation {
        guard let streamWithDeltas else {
            return try await explainStreaming(text, translatingTo: language, onMeaning: onMeaning)
        }
        streamCalls += 1
        return try await streamWithDeltas(onMeaningDelta, onMeaning)
    }
}

/// A bundled dictionary with two words: "apples" (a form of "apple") and "like".
private struct TinyDictionary: WordLooking {
    func lookUp(_ word: String) async throws -> WordEntry { throw NoDictionary() }

    func bundledEntry(for word: String) -> WordEntry? {
        let senses = [Sense(pos: "noun", cefr: "A1", simpleDefinition: "a thing", vietnamese: "x", examples: [])]
        switch word.lowercased() {
        case "apples": return WordEntry(word: "apple", ipa: nil, senses: senses,
                                        matchedForm: MatchedForm(text: "apples", type: "plural"))
        case "like": return WordEntry(word: "like", ipa: nil, senses: senses, matchedForm: nil)
        default: return nil
        }
    }
}

/// Writes down the store's state each time the fake translator asks, so the test can see the order.
@MainActor
private final class StateProbe {
    var store: TranslationStore?
    var seen: [String] = []

    func note() {
        guard let store else { return }
        seen.append(Self.label(store.state))
    }

    static func label(_ state: TranslationState) -> String {
        switch state {
        case .idle: "idle"
        case .loading: "loading"
        case let .meaning(partial): "meaning: \(partial.meaning)"
        case .loaded: "loaded"
        case .loadedWord: "word"
        case let .failed(message, _): "failed: \(message)"
        }
    }
}

private struct NoDictionary: Error {}

@MainActor
struct TranslationStoreStreamingTests {
    private func makeStore(_ translator: StreamingFake) -> TranslationStore {
        let store = TranslationStore(makeTranslator: { translator }, makeWordLookup: { throw NoDictionary() })
        store.inputText = "He was reluctant to leave"
        return store
    }

    @Test func stateGoesFromLoadingToMeaningToLoaded() async {
        let probe = StateProbe()
        let translator = StreamingFake { onMeaning in
            await probe.note() // before the meaning
            await onMeaning("He did not want to leave.")
            await probe.note() // after the meaning, before the rest
            return fullAnswer
        }
        let store = makeStore(translator)
        probe.store = store

        await store.translate()

        #expect(probe.seen == ["loading", "meaning: He did not want to leave."])
        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(StateProbe.label(store.state))")
            return
        }
        #expect(result.explanation == fullAnswer)
        #expect(result.sentence == "He was reluctant to leave")
        #expect(translator.plainCalls == 0)
    }

    @Test func theMeaningKeepsTheSentenceAndTheLanguageFromTheStartOfTheRequest() async {
        let probe = StateProbe()
        var captured: SentenceMeaning?
        let translator = StreamingFake { onMeaning in
            await onMeaning("He did not want to leave.")
            await MainActor.run { if case let .meaning(partial) = probe.store?.state { captured = partial } }
            return fullAnswer
        }
        let store = makeStore(translator)
        store.targetLanguage = .korean
        probe.store = store

        await store.translate()

        #expect(captured == SentenceMeaning(sentence: "He was reluctant to leave",
                                            meaning: "He did not want to leave.", language: .korean))
    }

    @Test func aCachedAnswerGoesStraightToLoaded() async {
        let probe = StateProbe()
        let translator = StreamingFake { _ in
            await probe.note()
            return fullAnswer // no onMeaning call, like a cache hit
        }
        let store = makeStore(translator)
        probe.store = store

        await store.translate()

        #expect(probe.seen == ["loading"])
        #expect(StateProbe.label(store.state) == "loaded")
    }

    @Test func aNewRequestCannotStartWhileOnlyTheMeaningIsThere() async {
        let probe = StateProbe()
        var canTranslateWhileMeaningShows: Bool?
        let translator = StreamingFake { onMeaning in
            await onMeaning("He did not want to leave.")
            await MainActor.run { canTranslateWhileMeaningShows = probe.store?.canTranslate }
            return fullAnswer
        }
        let store = makeStore(translator)
        probe.store = store

        await store.translate()

        #expect(canTranslateWhileMeaningShows == false)
        #expect(store.canTranslate == true)
    }

    // MARK: - Fallback

    @Test func ifTheStreamBreaksTheOldEndpointIsTriedOnce() async {
        let translator = StreamingFake { _ in throw BackendError.streamEnded }
        let store = makeStore(translator)

        await store.translate()

        #expect(translator.streamCalls == 1)
        #expect(translator.plainCalls == 1)
        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(StateProbe.label(store.state))")
            return
        }
        #expect(result.explanation == fullAnswer)
    }

    @Test func theFallbackAlsoWorksAfterTheMeaningWasShown() async {
        let translator = StreamingFake { onMeaning in
            await onMeaning("He did not want to leave.")
            throw URLError(.networkConnectionLost)
        }
        let store = makeStore(translator)

        await store.translate()

        #expect(translator.plainCalls == 1)
        #expect(StateProbe.label(store.state) == "loaded")
    }

    @Test func ifTheFallbackFailsToTheExistingErrorShows() async {
        let translator = StreamingFake { _ in throw URLError(.timedOut) }
        translator.plain = .failure(BackendError.badStatus(502, detail: "The translation service is not available. Try again later."))
        let store = makeStore(translator)

        await store.translate()

        #expect(translator.plainCalls == 1)
        #expect(StateProbe.label(store.state)
            == "failed: The translation service is not available. Try again later. (status 502)")
    }

    @Test func aServerError502FromTheStreamAlsoFallsBack() async {
        let translator = StreamingFake { _ in throw BackendError.badStatus(502, detail: "Try again later.") }
        let store = makeStore(translator)

        await store.translate()

        #expect(translator.plainCalls == 1)
    }

    @Test func aFinalAnswerFrom422IsNotRetried() async {
        let translator = StreamingFake { _ in
            throw BackendError.badStatus(422, detail: "This doesn't look like English. Type an English word or sentence.")
        }
        let store = makeStore(translator)

        await store.translate()

        #expect(translator.plainCalls == 0)
        #expect(StateProbe.label(store.state)
            == "failed: This doesn't look like English. Type an English word or sentence. (status 422)")
    }

    @Test func aFailureAfterTheMeaningReplacesItWithTheError() async {
        let translator = StreamingFake { onMeaning in
            await onMeaning("He did not want to leave.")
            throw BackendError.badStatus(422, detail: "Claude declined to answer this text.")
        }
        let store = makeStore(translator)

        await store.translate()

        #expect(StateProbe.label(store.state) == "failed: Claude declined to answer this text. (status 422)")
    }

    @Test func aTranslatorWithoutStreamingStillWorks() async {
        // FakeTranslator-style: only `explain` is written, `explainStreaming` is the default one.
        struct PlainOnly: Translating {
            func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
                fullAnswer
            }
        }
        let store = TranslationStore(makeTranslator: { PlainOnly() }, makeWordLookup: { throw NoDictionary() })
        store.inputText = "He was reluctant to leave"

        await store.translate()

        #expect(StateProbe.label(store.state) == "loaded")
    }

    // MARK: - Word by word (TASK-73)

    @Test func theMeaningGrowsFromPiecesThenTheFullMeaningReplacesIt() async {
        let probe = StateProbe()
        let translator = StreamingFake { _ in fullAnswer }
        translator.streamWithDeltas = { onDelta, onMeaning in
            await onDelta("He did ")
            await probe.note()
            await onDelta("not want")
            await probe.note()
            await onMeaning("He did not want to leave.")
            await probe.note()
            return fullAnswer
        }
        let store = makeStore(translator)
        probe.store = store

        await store.translate()

        #expect(probe.seen == [
            "meaning: He did ",
            "meaning: He did not want",
            "meaning: He did not want to leave.",
        ])
        #expect(StateProbe.label(store.state) == "loaded")
    }

    @Test func theFullMeaningWinsWhenAPieceIsMissing() async {
        let probe = StateProbe()
        let translator = StreamingFake { _ in fullAnswer }
        translator.streamWithDeltas = { onDelta, onMeaning in
            await onDelta("He did ")
            // "not want" never arrives
            await onMeaning("He did not want to leave.")
            await probe.note()
            return fullAnswer
        }
        let store = makeStore(translator)
        probe.store = store

        await store.translate()

        #expect(probe.seen == ["meaning: He did not want to leave."])
    }

    @Test func aLatePieceAfterTheFullMeaningIsIgnored() async {
        let probe = StateProbe()
        let translator = StreamingFake { _ in fullAnswer }
        translator.streamWithDeltas = { onDelta, onMeaning in
            await onMeaning("He did not want to leave.")
            await onDelta(" extra")
            await probe.note()
            return fullAnswer
        }
        let store = makeStore(translator)
        probe.store = store

        await store.translate()

        #expect(probe.seen == ["meaning: He did not want to leave."])
    }

    @Test func piecesKeepTheSentenceAndLanguageOfTheRequest() async {
        let probe = StateProbe()
        var captured: SentenceMeaning?
        let translator = StreamingFake { _ in fullAnswer }
        translator.streamWithDeltas = { onDelta, _ in
            await onDelta("He ")
            await MainActor.run { if case let .meaning(partial) = probe.store?.state { captured = partial } }
            return fullAnswer
        }
        let store = makeStore(translator)
        store.targetLanguage = .korean
        probe.store = store

        await store.translate()

        #expect(captured == SentenceMeaning(sentence: "He was reluctant to leave", meaning: "He ", language: .korean))
    }

    @Test func aNewRequestCannotStartWhilePiecesAreArriving() async {
        let probe = StateProbe()
        var canTranslate: Bool?
        let translator = StreamingFake { _ in fullAnswer }
        translator.streamWithDeltas = { onDelta, _ in
            await onDelta("He ")
            await MainActor.run { canTranslate = probe.store?.canTranslate }
            return fullAnswer
        }
        let store = makeStore(translator)
        probe.store = store

        await store.translate()

        #expect(canTranslate == false)
    }

    @Test func aFailureAfterSomePiecesShowsTheError() async {
        let translator = StreamingFake { _ in fullAnswer }
        translator.streamWithDeltas = { onDelta, _ in
            await onDelta("He did ")
            throw BackendError.badStatus(422, detail: "Claude declined to answer this text.")
        }
        let store = makeStore(translator)

        await store.translate()

        #expect(StateProbe.label(store.state) == "failed: Claude declined to answer this text. (status 422)")
    }

    // MARK: - The sentence shows at once (TASK-74, TASK-89.2)

    private func makeStore(_ translator: StreamingFake, dictionary: TinyDictionary) -> TranslationStore {
        let store = TranslationStore(makeTranslator: { translator }, makeWordLookup: { dictionary })
        store.inputText = "I like apples"
        return store
    }

    @Test func theSentenceIsPendingBeforeAnyAnswerAndRemovedWhenItEnds() async {
        let probe = StateProbe()
        var duringLoading: PendingSentence?
        let translator = StreamingFake { _ in
            await MainActor.run { duringLoading = probe.store?.pendingSentence }
            return fullAnswer
        }
        let store = makeStore(translator, dictionary: TinyDictionary())
        probe.store = store

        await store.translate()

        #expect(duringLoading == PendingSentence(sentence: "I like apples", language: .vietnamese))
        #expect(store.pendingSentence == nil)
    }

    @Test func aFailedRequestRemovesThePendingSentenceAndShowsTheError() async {
        let translator = StreamingFake { _ in
            throw BackendError.badStatus(422, detail: "This doesn't look like English. Type an English word or sentence.")
        }
        let store = makeStore(translator, dictionary: TinyDictionary())

        await store.translate()

        #expect(store.pendingSentence == nil)
        #expect(StateProbe.label(store.state)
            == "failed: This doesn't look like English. Type an English word or sentence. (status 422)")
    }

    @Test func aWordSearchHasNoPendingSentence() async {
        let store = TranslationStore(makeTranslator: { StreamingFake { _ in fullAnswer } },
                                     makeWordLookup: { TinyDictionary() })
        store.inputText = "apples"

        await store.translate()

        #expect(store.pendingSentence == nil)
    }
}
