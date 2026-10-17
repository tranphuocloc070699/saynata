//
//  CopiedTextExplainerTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// A clipboard with fixed text.
private struct FakeClipboard: ClipboardReading {
    var copied: String?
    func text() async -> String? { copied }
}

/// Records what it was asked, so a test can check which path the text took.
private final class Calls: @unchecked Sendable {
    var words: [String] = []
    var sentences: [String] = []
}

/// A word lookup with a fixed answer (or error), and an optional bundle for sentence words.
private struct FakeWordLookup: WordLooking {
    var result: Result<WordEntry, Error> = .success(.run)
    var bundled: [String: WordEntry] = [:]
    var delay: Duration?
    let calls: Calls

    func lookUp(_ word: String) async throws -> WordEntry {
        calls.words.append(word)
        if let delay { try await Task.sleep(for: delay) }
        return try result.get()
    }

    func bundledEntry(for word: String) -> WordEntry? { bundled[word] }
}

/// A translator with a fixed answer (or error).
private struct FakeTranslator: Translating {
    var result: Result<Explanation, Error>
    var delay: Duration?
    let calls: Calls

    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        calls.sentences.append(text)
        if let delay { try await Task.sleep(for: delay) }
        return try result.get()
    }
}

private let sampleExplanation = Explanation(meaning: "She did not want to go.", words: [], translation: "Cô ấy không muốn đi.")

private func makeExplainer(
    clipboard: String?,
    word: Result<WordEntry, Error> = .success(.run),
    sentence: Result<Explanation, Error> = .success(sampleExplanation),
    bundled: [String: WordEntry] = [:],
    delay: Duration? = nil,
    calls: Calls
) -> CopiedTextExplainer {
    var explainer = CopiedTextExplainer(
        clipboard: FakeClipboard(copied: clipboard),
        makeWordLookup: { FakeWordLookup(result: word, bundled: bundled, delay: delay, calls: calls) },
        makeTranslator: { FakeTranslator(result: sentence, delay: delay, calls: calls) })
    explainer.timeLimit = .milliseconds(200)
    return explainer
}

struct CopiedTextExplainerTests {
    // MARK: - Word or sentence

    @Test func oneWordUsesTheWordLookup() async {
        let calls = Calls()
        let result = await makeExplainer(clipboard: "  Run! \n", calls: calls).explain()
        #expect(result == .word(.run))
        #expect(calls.words == ["Run"])
        #expect(calls.sentences.isEmpty)
    }

    @Test func aWordWithAnArticleIsStillAWord() async {
        let calls = Calls()
        _ = await makeExplainer(clipboard: "an apple", word: .success(.apple), calls: calls).explain()
        #expect(calls.words == ["apple"])
    }

    @Test func moreThanOneWordUsesTheSentenceAPI() async {
        let calls = Calls()
        let result = await makeExplainer(clipboard: "She was reluctant to go.", calls: calls).explain()
        #expect(result == .sentence("She was reluctant to go.", sampleExplanation))
        #expect(calls.sentences == ["She was reluctant to go."])
        #expect(calls.words.isEmpty)
    }

    @Test func aBooksCitationIsRemovedBeforeExplaining() async {
        let calls = Calls()
        let copied = "\u{201C}It was a bright cold day.\u{201D}\n\nExcerpt From\nA Book\nThis material may be protected by copyright."
        _ = await makeExplainer(clipboard: copied, calls: calls).explain()
        #expect(calls.sentences == ["It was a bright cold day."])
    }

    @Test func textFromTheShortcutWinsOverTheClipboard() async {
        let calls = Calls()
        _ = await makeExplainer(clipboard: "ignored text here", calls: calls).explain("run")
        #expect(calls.words == ["run"])
    }

    // MARK: - Text that is not explained

    @Test(arguments: [nil, "", "   \n "])
    func anEmptyClipboardShowsAMessage(copied: String?) async {
        let calls = Calls()
        let result = await makeExplainer(clipboard: copied, calls: calls).explain()
        guard case let .problem(title, _) = result else {
            Issue.record("Expected a problem, got \(result)")
            return
        }
        #expect(title == "Nothing to explain")
        #expect(calls.words.isEmpty && calls.sentences.isEmpty)
    }

    @Test func textOverTheLimitIsNotSent() async {
        let calls = Calls()
        let result = await makeExplainer(clipboard: String(repeating: "word ", count: 120), calls: calls).explain()
        guard case let .problem(title, _) = result else {
            Issue.record("Expected a problem, got \(result)")
            return
        }
        #expect(title == "Text too long")
        #expect(calls.sentences.isEmpty)
    }

    @Test(arguments: ["你好世界", "Tôi muốn đi học tiếng Anh mỗi ngày", "Привет как дела"])
    func nonEnglishTextIsNotSent(copied: String) async {
        let calls = Calls()
        let result = await makeExplainer(clipboard: copied, calls: calls).explain()
        guard case let .problem(title, _) = result else {
            Issue.record("Expected a problem, got \(result)")
            return
        }
        #expect(title == "Not English")
        #expect(calls.words.isEmpty && calls.sentences.isEmpty)
    }

    @Test(arguments: ["café", "I love sushi and ramen", "The naïve résumé was fine."])
    func englishWithForeignWordsIsStillSent(copied: String) {
        #expect(CopiedTextExplainer.looksLikeEnglish(copied))
    }

    @Test func aWordTheServerDoesNotKnowShowsItsMessage() async {
        let calls = Calls()
        let result = await makeExplainer(
            clipboard: "blorft", word: .failure(WordLookupError.notAnEnglishWord("blorft")), calls: calls
        ).explain()
        #expect(result == .problem(
            title: "Can't explain this", message: WordLookupError.notAnEnglishWord("blorft").localizedDescription))
    }

    // MARK: - Signed out, slow, failed

    @Test func aGuestAskingForANewWordIsToldToSignIn() async {
        let calls = Calls()
        let result = await makeExplainer(clipboard: "serendipity", word: .failure(BackendError.signInRequired), calls: calls)
            .explain()
        #expect(result == .signInNeeded("serendipity", localWords: []))
    }

    @Test func aGuestAskingForANewSentenceStillSeesTheBundledWords() async {
        let calls = Calls()
        let result = await makeExplainer(
            clipboard: "I run every morning.",
            sentence: .failure(BackendError.signInRequired),
            bundled: ["run": .run],
            calls: calls
        ).explain()
        guard case let .signInNeeded(text, localWords) = result else {
            Issue.record("Expected sign in, got \(result)")
            return
        }
        #expect(text == "I run every morning.")
        #expect(localWords.map(\.entry?.word) == ["run"])
    }

    @Test func aSlowAnswerShowsStillWorking() async {
        let calls = Calls()
        let result = await makeExplainer(clipboard: "She was reluctant to go.", delay: .seconds(5), calls: calls).explain()
        #expect(result == .slow("She was reluctant to go.", localWords: []))
    }

    @Test func aFastAnswerIsNotCutOff() async {
        let calls = Calls()
        let result = await makeExplainer(clipboard: "run", delay: .milliseconds(10), calls: calls).explain()
        #expect(result == .word(.run))
    }

    @Test func theServersNotEnglishAnswerIsAProblem() async {
        let calls = Calls()
        let result = await makeExplainer(
            clipboard: "asdf qwer zxcv",
            sentence: .failure(BackendError.badStatus(400, detail: "This doesn't look like English.")),
            calls: calls
        ).explain()
        #expect(result == .problem(title: "Can't explain this", message: "This doesn't look like English."))
    }

    @Test func noNetworkIsAFailureWithTheText() async {
        let calls = Calls()
        let result = await makeExplainer(
            clipboard: "She was reluctant to go.", sentence: .failure(URLError(.notConnectedToInternet)), calls: calls
        ).explain()
        guard case let .failed(text, _) = result else {
            Issue.record("Expected failed, got \(result)")
            return
        }
        #expect(text == "She was reluctant to go.")
    }

    // MARK: - Card

    @Test func theOpenLinkUsesTheTextOrTheWord() {
        #expect(CopiedTextResult.word(.run).text == "run")
        #expect(CopiedTextResult.word(.ran).text == "ran")
        #expect(CopiedTextResult.slow("a b", localWords: []).text == "a b")
        #expect(CopiedTextResult.problem(title: "x", message: "y").text == nil)
    }

    @Test func theTargetLanguageLineHasNoRepeats() {
        #expect(CopiedTextCard.uniqueJoined(["chạy", " chạy ", "", "điều hành"]) == "chạy; điều hành")
    }
}
