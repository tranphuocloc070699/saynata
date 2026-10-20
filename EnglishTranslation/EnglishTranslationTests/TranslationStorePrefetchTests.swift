//
//  TranslationStorePrefetchTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// Remembers what the prefetch asked for, and what the real requests asked for.
private final class Calls: @unchecked Sendable {
    var prefetchedWords: [String] = []
    var prefetchedSentences: [String] = []
    var explained: [String] = []
}

private struct FakeTranslator: Translating {
    let calls: Calls
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        calls.explained.append(text)
        return Explanation(meaning: "A greeting.", words: [], translation: "Xin chào")
    }

    func prefetchCachedExplanation(_ text: String, translatingTo language: TargetLanguage) async {
        calls.prefetchedSentences.append(text)
    }
}

private struct FakeWordLookup: WordLooking {
    let calls: Calls
    func lookUp(_ word: String) async throws -> WordEntry { .run }

    func prefetchCached(_ word: String) async {
        calls.prefetchedWords.append(word)
    }
}

private final class HistorySpy: HistoryStoring, @unchecked Sendable {
    var recorded = 0
    func record(_ item: SavedItem) async { recorded += 1 }
    func items() async -> [SavedItem] { [] }
    func remove(id: String) async {}
    func removeAll() async {}
}

/// Prefetch while typing (TASK-83.5). A short delay keeps the tests fast.
@MainActor
struct TranslationStorePrefetchTests {
    private let delay = Duration.milliseconds(30)
    private let wait = Duration.milliseconds(250)

    private func makeStore(_ calls: Calls, history: (any HistoryStoring)? = nil) -> TranslationStore {
        TranslationStore(
            makeTranslator: { FakeTranslator(calls: calls) },
            makeWordLookup: { FakeWordLookup(calls: calls) },
            history: history,
            prefetchDelay: delay)
    }

    @Test func oneWordIsPrefetchedAfterThePause() async throws {
        let calls = Calls()
        let store = makeStore(calls)
        store.inputText = "Serendipity!"
        #expect(calls.prefetchedWords.isEmpty) // not before the pause
        try await Task.sleep(for: wait)
        #expect(calls.prefetchedWords == ["Serendipity"])
        #expect(calls.prefetchedSentences.isEmpty)
        if case .idle = store.state {} else { Issue.record("Nothing should be shown, got \(store.state)") }
        #expect(store.isSignInSheetOpen == false)
    }

    @Test func sentenceIsPrefetchedAsASentence() async throws {
        let calls = Calls()
        let store = makeStore(calls)
        store.inputText = "I was very reluctant to leave the party early."
        try await Task.sleep(for: wait)
        #expect(calls.prefetchedSentences == ["I was very reluctant to leave the party early."])
        #expect(calls.prefetchedWords.isEmpty)
    }

    @Test func phraseTriesTheWordCardThenTheSentence() async throws {
        let calls = Calls()
        let store = makeStore(calls)
        store.inputText = "give up"
        try await Task.sleep(for: wait)
        #expect(calls.prefetchedWords == ["give up"])
        #expect(calls.prefetchedSentences == ["give up"])
    }

    @Test func typingAgainCancelsTheEarlierPrefetch() async throws {
        let calls = Calls()
        let store = makeStore(calls)
        store.inputText = "app"
        store.inputText = "appl"
        store.inputText = "apple"
        try await Task.sleep(for: wait)
        #expect(calls.prefetchedWords == ["apple"])
    }

    @Test func clearingTheTextCancelsThePrefetch() async throws {
        let calls = Calls()
        let store = makeStore(calls)
        store.inputText = "apple"
        store.inputText = ""
        try await Task.sleep(for: wait)
        #expect(calls.prefetchedWords.isEmpty)
    }

    @Test func tooLongTextIsNotPrefetched() async throws {
        let calls = Calls()
        let store = makeStore(calls)
        store.inputText = String(repeating: "a ", count: 300)
        try await Task.sleep(for: wait)
        #expect(calls.prefetchedSentences.isEmpty)
    }

    @Test func submitCancelsThePrefetchAndUsesTheNormalPath() async throws {
        let calls = Calls()
        let store = makeStore(calls)
        store.inputText = "Hello there"
        await store.translate()
        try await Task.sleep(for: wait)
        #expect(calls.prefetchedSentences.isEmpty)
        #expect(calls.explained == ["Hello there"])
        guard case .loaded = store.state else {
            Issue.record("Expected a result, got \(store.state)")
            return
        }
    }

    @Test func prefetchAloneNeverWritesHistory() async throws {
        let calls = Calls()
        let history = HistorySpy()
        let store = makeStore(calls, history: history)
        store.inputText = "Hello there"
        try await Task.sleep(for: wait)
        #expect(calls.prefetchedSentences == ["Hello there"])
        #expect(history.recorded == 0)

        await store.translate()
        #expect(history.recorded == 1)
    }
}
