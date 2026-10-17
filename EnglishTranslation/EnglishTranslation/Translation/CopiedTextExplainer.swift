//
//  CopiedTextExplainer.swift
//  EnglishTranslation
//

import Foundation
import NaturalLanguage

/// Anything that can read the text on the clipboard. `SystemClipboard` (UIKit) is the real one; tests use a fake.
protocol ClipboardReading: Sendable {
    func content() async -> ClipboardContent
}

/// What the clipboard gave.
enum ClipboardContent: Equatable, Sendable {
    case text(String)
    /// No text was copied.
    case empty
    /// There is text, but iOS did not let Saynata read it ("Don't Allow Paste", or Paste from Other Apps is Deny).
    case notAllowed
}

/// What the "Explain copied text" card shows (TASK-87).
enum CopiedTextResult: Equatable, Sendable {
    /// One word: its dictionary entry.
    case word(WordEntry)
    /// A sentence and its explanation.
    case sentence(String, Explanation)
    /// Text the app does not explain (empty, too long, not English). Shown as a title and a short message.
    case problem(title: String, message: String)
    /// A guest asked for an answer that is not cached (401 `sign_in_required`). `localWords` are the sentence's
    /// bundled dictionary words, shown so the card is not empty.
    case signInNeeded(String, localWords: [SentenceWordInfo])
    /// No answer before the time limit. `localWords` as above.
    case slow(String, localWords: [SentenceWordInfo])
    /// Any other error, with a message for the user.
    case failed(String, message: String)

    /// The text the card is about, for the "Open in Saynata" link. Nil for a problem (nothing to open).
    var text: String? {
        switch self {
        case let .word(entry): entry.matchedForm?.text ?? entry.word
        case let .sentence(text, _), let .signInNeeded(text, _), let .slow(text, _), let .failed(text, _): text
        case .problem: nil
        }
    }
}

/// Reads the clipboard and explains it, the same way as the app: one word (or "an apple") goes to the word lookup
/// (bundle, phone cache, backend), more goes to the sentence API (phone cache, backend). Used by
/// `ExplainCopiedTextIntent`, which shows the result as a Shortcuts card over the current app.
/// On the main actor like `TranslationStore`, because `TranslatorFactory` is; the network calls still run off it.
@MainActor
struct CopiedTextExplainer {
    let clipboard: any ClipboardReading
    let makeWordLookup: @MainActor () throws -> any WordLooking
    let makeTranslator: @MainActor () throws -> any Translating
    var language: TargetLanguage = .vietnamese
    /// A Shortcuts card cannot show an answer that is still coming, so after this many seconds the card says
    /// "still working" instead. Bundled and cached answers come back long before this.
    var timeLimit: Duration = .seconds(8)

    /// Explains `text`, or the clipboard when `text` is nil.
    func explain(_ text: String? = nil) async -> CopiedTextResult {
        let copied: String
        if let text {
            copied = text
        } else {
            switch await clipboard.content() {
            case let .text(text):
                copied = text
            case .empty:
                copied = ""
            case .notAllowed:
                return .problem(
                    title: "Paste is blocked",
                    message: "iOS did not let Saynata read what you copied. Tap Allow Paste next time, or set "
                        + "Settings > Apps > Saynata > Paste from Other Apps to Allow.")
            }
        }
        let cleaned = CopiedText.cleaned(copied)

        switch TextRoute(text: cleaned) {
        case .empty:
            return .problem(title: "Nothing to explain", message: "Copy some English text, then try again.")
        case let .tooLong(length):
            return .problem(
                title: "Text too long",
                message: "This text has \(length) characters. The limit is \(TextRoute.maxLength). Copy a shorter part.")
        case .word, .sentence:
            break
        }
        guard Self.looksLikeEnglish(cleaned) else {
            return .problem(title: "Not English", message: "Saynata explains English text. Copy some English, then try again.")
        }

        let lookup = try? makeWordLookup()
        if let word = CacheKeys.wordQuery(cleaned) {
            return await explainWord(word, lookup: lookup)
        }
        return await explainSentence(cleaned, lookup: lookup)
    }

    private func explainWord(_ word: String, lookup: (any WordLooking)?) async -> CopiedTextResult {
        guard let lookup else {
            return .failed(word, message: "Saynata could not open its dictionary.")
        }
        do {
            return .word(try await withTimeLimit { try await lookup.lookUp(word) })
        } catch {
            return result(for: error, text: word, localWords: [])
        }
    }

    private func explainSentence(_ sentence: String, lookup: (any WordLooking)?) async -> CopiedTextResult {
        // The bundled words are found offline, so a slow or signed-out card still has something to read.
        let localWords = SentenceWordFinder.extraWords(
            in: sentence, skipping: [], bundledEntry: { lookup?.bundledEntry(for: $0) })
        do {
            let translator = try makeTranslator()
            let language = language
            // `explain` answers from the phone cache first, so a cached sentence never waits for the network.
            let explanation = try await withTimeLimit { try await translator.explain(sentence, translatingTo: language) }
            return .sentence(sentence, explanation)
        } catch {
            return result(for: error, text: sentence, localWords: localWords)
        }
    }

    /// The card for an error.
    private func result(for error: Error, text: String, localWords: [SentenceWordInfo]) -> CopiedTextResult {
        switch error {
        case BackendError.signInRequired:
            return .signInNeeded(text, localWords: localWords)
        case is TimeLimitReached:
            return .slow(text, localWords: localWords)
        case let error as URLError where error.code == .timedOut:
            return .slow(text, localWords: localWords)
        case let BackendError.badStatus(code, detail?) where (400..<500).contains(code):
            // The server's own "no", for example "This doesn't look like English."
            return .problem(title: "Can't explain this", message: detail)
        case let error as WordLookupError:
            return .problem(title: "Can't explain this", message: error.localizedDescription)
        default:
            return .failed(text, message: error.localizedDescription)
        }
    }

    /// Runs `operation`, or throws `TimeLimitReached` after `timeLimit`.
    private func withTimeLimit<T: Sendable>(_ operation: @escaping @Sendable () async throws -> T) async throws -> T {
        let timeLimit = timeLimit
        return try await withThrowingTaskGroup(of: T.self) { group in
            group.addTask { try await operation() }
            group.addTask {
                try await Task.sleep(for: timeLimit)
                throw TimeLimitReached()
            }
            defer { group.cancelAll() }
            // The first task to finish wins; the other one is cancelled.
            return try await group.next()!
        }
    }

    /// False for text that is clearly not English, so it never costs a backend call: no Latin letters at all
    /// ("你好"), or three or more words that the on-device recognizer is sure are another language
    /// ("Tôi muốn đi học"). Short or mixed text gets the benefit of the doubt; the server checks again.
    nonisolated static func looksLikeEnglish(_ text: String) -> Bool {
        let hasLatinLetter = text.unicodeScalars.contains { ("a"..."z").contains($0) || ("A"..."Z").contains($0) }
        guard hasLatinLetter else { return false }

        guard text.split(whereSeparator: \.isWhitespace).count >= 3 else { return true }
        let recognizer = NLLanguageRecognizer()
        recognizer.processString(text)
        guard let (language, confidence) = recognizer.languageHypotheses(withMaximum: 1).first else { return true }
        return language == .english || confidence < 0.9
    }
}

/// The answer took longer than `CopiedTextExplainer.timeLimit`.
struct TimeLimitReached: Error {}
