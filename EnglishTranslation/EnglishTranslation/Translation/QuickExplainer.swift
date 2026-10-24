//
//  QuickExplainer.swift
//  EnglishTranslation
//

import Foundation
import NaturalLanguage

/// What the "Quick explain" card shows (TASK-90).
enum QuickExplainResult: Equatable, Sendable {
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

/// Explains the text the user typed or pasted into the "Quick explain" box, the same way as the app: one word
/// (or "an apple") goes to the word lookup (bundle, phone cache, backend), more goes to the sentence API (phone cache,
/// backend). Used by `QuickExplainIntent`, which shows the result as a card over the current app (TASK-90).
/// On the main actor like `TranslationStore`, because `TranslatorFactory` is; the network calls still run off it.
@MainActor
struct QuickExplainer {
    let makeWordLookup: @MainActor () throws -> any WordLooking
    let makeTranslator: @MainActor () throws -> any Translating
    var language: TargetLanguage = .vietnamese
    /// A Shortcuts card cannot show an answer that is still coming, so after this many seconds the card says
    /// "still working" instead. Bundled and cached answers come back long before this.
    var timeLimit: Duration = .seconds(8)
    /// Where the word's language line comes from (TASK-106.3).
    var lines: LanguageLines = .shared
    /// The line has its own, shorter limit: the card is useful without it.
    var lineTimeLimit: Duration = .seconds(3)

    /// Explains `text`. A pasted Apple Books citation is left out, as in the app.
    func explain(_ text: String) async -> QuickExplainResult {
        let cleaned = CopiedText.cleaned(text)

        switch TextRoute(text: cleaned) {
        case .empty:
            return .problem(title: "Nothing to explain", message: "Type or paste some English, then try again.")
        case let .tooLong(length):
            return .problem(
                title: "Text too long",
                message: "This text has \(length) characters. The limit is \(TextRoute.maxLength). Try a shorter part.")
        case .word, .sentence:
            break
        }
        guard Self.looksLikeEnglish(cleaned) else {
            return .problem(title: "Not English", message: "Saynata explains English text. Type or paste some English, then try again.")
        }

        let lookup = try? makeWordLookup()
        if let word = CacheKeys.wordQuery(cleaned) {
            return await explainWord(word, lookup: lookup)
        }
        if let phrase = CacheKeys.phraseQuery(cleaned),
           let entry = try? await withTimeLimit({ await lookup?.phraseEntry(for: phrase) }) {
            return .word(entry)
        }
        return await explainSentence(cleaned, lookup: lookup)
    }

    /// The backend line for the first meaning of a word card, in the picked language. Nil for a sentence (its
    /// explanation already has the line), for English, and when the backend has no line in time: the card then uses
    /// the dictionary text (Vietnamese) or shows no line (no Apple Translation over another app).
    func languageLine(for result: QuickExplainResult) async -> String? {
        guard language.hasTargetLanguageRow, case let .word(entry) = result,
              let english = entry.senses.first?.simpleDefinition, !english.isEmpty else { return nil }
        let language = language
        // Race the request against the timer. The request goes on in the background and fills the cache.
        let limit = lineTimeLimit
        let lines = lines
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let finish = OnceResumer(continuation)
            Task { @MainActor in
                await lines.load([english], in: language)
                finish.resume()
            }
            Task { @MainActor in
                try? await Task.sleep(for: limit)
                finish.resume()
            }
        }
        return lines.line(for: english, in: language)
    }

    private func explainWord(_ word: String, lookup: (any WordLooking)?) async -> QuickExplainResult {
        guard let lookup else {
            return .failed(word, message: "Saynata could not open its dictionary.")
        }
        do {
            return .word(try await withTimeLimit { try await lookup.lookUp(word) })
        } catch WordLookupError.notAnEnglishWord {
            // A missing word-list entry may still be understandable as text.
            return await explainSentence(word, lookup: lookup)
        } catch {
            return result(for: error, text: word, localWords: [])
        }
    }

    private func explainSentence(_ sentence: String, lookup: (any WordLooking)?) async -> QuickExplainResult {
        // The bundled words are found offline, so a slow or signed-out card still has something to read.
        let localWords = Self.bundledWords(in: sentence, bundledEntry: { lookup?.bundledEntry(for: $0) })
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
    private func result(for error: Error, text: String, localWords: [SentenceWordInfo]) -> QuickExplainResult {
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

/// Resumes a continuation the first time `resume()` is called; later calls do nothing.
@MainActor
private final class OnceResumer {
    private var continuation: CheckedContinuation<Void, Never>?

    init(_ continuation: CheckedContinuation<Void, Never>) { self.continuation = continuation }

    func resume() {
        continuation?.resume()
        continuation = nil
    }
}

/// The answer took longer than `QuickExplainer.timeLimit`.
struct TimeLimitReached: Error {}

// MARK: - Bundled words of a sentence

extension QuickExplainer {
    /// Only these kinds of words are kept. Pronouns, articles, prepositions, conjunctions and numbers are not.
    private static let contentClasses: Set<NLTag> = [.noun, .verb, .adjective, .adverb]

    /// Helper verbs. "be", "have" and "do" mostly help other verbs, and modals ("can", "will") always do.
    private static let helperWords: Set<String> = [
        "be", "have", "do", "can", "could", "will", "would", "shall", "should", "may", "might", "must",
    ]

    /// The nouns, verbs, adjectives and adverbs of the sentence that are in the bundled dictionary, in order and
    /// without repeats. Works offline, so a slow or signed-out card still has words to read. (This was
    /// `SentenceWordFinder`, which TASK-89.2 removed from the app's sentence result.)
    static func bundledWords(in sentence: String, bundledEntry: (String) -> WordEntry?) -> [SentenceWordInfo] {
        let tagger = NLTagger(tagSchemes: [.lexicalClass])
        tagger.string = sentence
        tagger.setLanguage(.english, range: sentence.startIndex..<sentence.endIndex)

        var seen = Set<String>()
        var found: [SentenceWordInfo] = []
        tagger.enumerateTags(
            in: sentence.startIndex..<sentence.endIndex,
            unit: .word,
            scheme: .lexicalClass,
            options: [.omitWhitespace, .omitPunctuation, .omitOther]
        ) { tag, range in
            guard let tag, contentClasses.contains(tag) else { return true }
            let token = String(sentence[range])
            guard !seen.contains(token.lowercased()),
                  let entry = bundledEntry(token),
                  let sense = entry.senses.first,
                  !helperWords.contains(entry.word.lowercased()),
                  !seen.contains(entry.word.lowercased())
            else { return true }
            seen.insert(token.lowercased())
            seen.insert(entry.word.lowercased())
            let word = Explanation.Word(word: token, partOfSpeech: sense.pos, meaning: sense.simpleDefinition)
            found.append(SentenceWordInfo(word: word, entry: entry))
            return true
        }
        return found
    }
}
