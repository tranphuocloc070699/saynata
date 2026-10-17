//
//  StartFromClipboardTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// A clipboard with fixed content.
private struct FakeClipboard: ClipboardReading {
    var result: ClipboardContent
    func content() async -> ClipboardContent { result }
}

/// Remembers which word and sentence were asked for.
private final class Calls: @unchecked Sendable {
    var words: [String] = []
    var sentences: [String] = []
}

private struct FakeWordLookup: WordLooking {
    let calls: Calls
    func lookUp(_ word: String) async throws -> WordEntry {
        calls.words.append(word)
        return .run
    }
}

private struct FakeTranslator: Translating {
    let calls: Calls
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        calls.sentences.append(text)
        return Explanation(meaning: "She did not want to go.", words: [], translation: "Cô ấy không muốn đi.")
    }
}

/// Back Tap opens the app on the copied text (TASK-87).
@MainActor
struct StartFromClipboardTests {
    private func makeStore(_ calls: Calls) -> TranslationStore {
        TranslationStore(
            makeTranslator: { FakeTranslator(calls: calls) },
            makeWordLookup: { FakeWordLookup(calls: calls) })
    }

    @Test func copiedWordIsPastedAndLookedUp() async {
        let calls = Calls()
        let store = makeStore(calls)
        let wantsKeyboard = await store.startFromClipboard(FakeClipboard(result: .text("  run \n")))
        #expect(store.inputText == "run")
        guard case let .loadedWord(entry) = store.state else {
            Issue.record("Expected a word card, got \(store.state)")
            return
        }
        #expect(entry == .run)
        #expect(calls.words == ["run"])
        #expect(wantsKeyboard == false)
    }

    @Test func copiedSentenceIsPastedAndExplained() async {
        let calls = Calls()
        let store = makeStore(calls)
        await store.startFromClipboard(FakeClipboard(result: .text("She was reluctant to go.")))
        #expect(store.inputText == "She was reluctant to go.")
        #expect(calls.sentences == ["She was reluctant to go."])
    }

    @Test func aBooksCitationIsLeftOut() async {
        let calls = Calls()
        let store = makeStore(calls)
        let copied = "\u{201C}It was a bright cold day.\u{201D}\n\nExcerpt From\nA Book\nThis material may be protected by copyright."
        await store.startFromClipboard(FakeClipboard(result: .text(copied)))
        #expect(store.inputText == "It was a bright cold day.")
    }

    @Test(arguments: [ClipboardContent.empty, .notAllowed, .text("   \n")])
    func nothingUsefulCopiedClearsTheInputAndAsksForTheKeyboard(content: ClipboardContent) async {
        let calls = Calls()
        let store = makeStore(calls)
        store.inputText = "old text"
        await store.translate()
        store.isSignInSheetOpen = true
        let wantsKeyboard = await store.startFromClipboard(FakeClipboard(result: content))
        #expect(store.inputText == "")
        guard case .idle = store.state else {
            Issue.record("Expected an empty result, got \(store.state)")
            return
        }
        #expect(wantsKeyboard)
        #expect(store.isSignInSheetOpen == false)
        #expect(calls.sentences == ["old text"])
    }

    @Test func eachRequestIsCounted() {
        let launch = CopiedTextLaunch()
        launch.request()
        launch.request()
        #expect(launch.requestCount == 2)
    }
}
