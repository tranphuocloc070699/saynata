//
//  SentencePartStoreTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// A translator for the popup: the test decides what the stream does.
private final class PartFake: Translating, @unchecked Sendable {
    typealias Callback = @MainActor @Sendable (String) -> Void
    var run: @Sendable (_ onDelta: Callback, _ onMeaning: Callback) async throws -> Explanation
    private(set) var texts: [String] = []
    private(set) var plainCalls = 0

    init(run: @escaping @Sendable (Callback, Callback) async throws -> Explanation) {
        self.run = run
    }

    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        plainCalls += 1
        return Explanation(meaning: "plain", words: [], translation: "plain-t")
    }

    func explainStreaming(_ text: String, translatingTo language: TargetLanguage,
                          onMeaningDelta: @escaping Callback, onMeaning: @escaping Callback) async throws -> Explanation {
        texts.append(text)
        return try await run(onMeaningDelta, onMeaning)
    }
}

/// Collects what the store showed at each step.
@MainActor
private final class PhaseLog {
    var store: SentencePartStore?
    var phases: [SentencePartStore.Phase] = []
    func note() {
        if let store { phases.append(store.phase) }
    }
}

@MainActor
struct SentencePartStoreTests {
    private let answer = Explanation(meaning: "made it hard to see", words: [], translation: "làm mờ")

    @Test func startsWithASpinnerThenStreamsTheSummaryThenLoads() async {
        let log = PhaseLog()
        let fake = PartFake { [answer] onDelta, onMeaning in
            await MainActor.run { log.note() }
            await onDelta("made it ")
            await MainActor.run { log.note() }
            await onDelta("hard")
            await MainActor.run { log.note() }
            await onMeaning("made it hard to see")
            await MainActor.run { log.note() }
            return answer
        }
        let store = SentencePartStore(makeTranslator: { fake })
        log.store = store

        await store.explain("blurred the edges", language: .vietnamese)

        #expect(log.phases == [.loading, .summary("made it "), .summary("made it hard"), .summary("made it hard to see")])
        #expect(store.phase == .loaded(summary: "made it hard to see", translation: "làm mờ"))
        #expect(store.part == "blurred the edges")
        #expect(fake.texts == ["blurred the edges"])
    }

    @Test func aLateDeltaAfterTheFullSummaryIsIgnored() async {
        let fake = PartFake { [answer] onDelta, onMeaning in
            await onMeaning("made it hard to see")
            await onDelta(" extra")
            return answer
        }
        let store = SentencePartStore(makeTranslator: { fake })

        await store.explain("blurred the edges", language: .vietnamese)

        #expect(store.phase.summaryText == "made it hard to see")
    }

    @Test func aCachedAnswerWithNoPiecesGoesStraightToLoaded() async {
        let fake = PartFake { [answer] _, _ in answer }
        let store = SentencePartStore(makeTranslator: { fake })

        await store.explain("blurred the edges", language: .korean)

        #expect(store.phase == .loaded(summary: "made it hard to see", translation: "làm mờ"))
        #expect(store.language == .korean)
    }

    @Test func aClearNoFromTheServerShowsTheMessageAndIsNotRetried() async {
        let fake = PartFake { _, _ in
            throw BackendError.badStatus(422, detail: "This doesn't look like English. Type an English word or sentence.")
        }
        let store = SentencePartStore(makeTranslator: { fake })

        await store.explain("xyz qwe", language: .vietnamese)

        guard case let .failed(message) = store.phase else {
            Issue.record("Expected .failed")
            return
        }
        #expect(message.contains("doesn't look like English"))
        #expect(fake.plainCalls == 0)
    }

    @Test func aBrokenStreamTriesThePlainCallOnce() async {
        let fake = PartFake { _, _ in throw URLError(.networkConnectionLost) }
        let store = SentencePartStore(makeTranslator: { fake })

        await store.explain("blurred the edges", language: .vietnamese)

        #expect(fake.plainCalls == 1)
        #expect(store.phase == .loaded(summary: "plain", translation: "plain-t"))
    }

    @Test func aMissingBackendSettingShowsAnError() async {
        struct Broken: LocalizedError { var errorDescription: String? { "No backend." } }
        let store = SentencePartStore(makeTranslator: { throw Broken() })

        await store.explain("blurred the edges", language: .vietnamese)

        #expect(store.phase == .failed("No backend."))
    }

    @Test func aTappedWordHasNoEntryYetSoTheSheetLooksItUp() {
        let info = SentenceWordInfo(tappedWord: "blurred")
        #expect(info.word == "blurred")
        #expect(info.entry == nil)
        #expect(info.canOpenWordCard)
        #expect(info.pillText == nil)
    }
}
