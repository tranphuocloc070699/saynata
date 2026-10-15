//
//  TranslationStore.swift
//  EnglishTranslation
//

import Foundation
import Observation

/// Holds the text the user typed and the result of translating it.
/// Created once in `EnglishTranslationApp` and shared with `.environment`.
@Observable
@MainActor
final class TranslationStore {
    var inputText = ""
    /// The language of the translation at the bottom of the result.
    var targetLanguage: TargetLanguage = .vietnamese
    private(set) var state: TranslationState = .idle

    private let makeTranslator: @MainActor () throws -> any Translating
    private let makeWordLookup: @MainActor () throws -> any WordLooking

    /// - Parameter makeTranslator: Builds the translator on each request, so a
    ///   missing setting shows as a `.failed` state instead of crashing at launch.
    init(
        makeTranslator: @escaping @MainActor () throws -> any Translating = TranslatorFactory.makeTranslator,
        makeWordLookup: @escaping @MainActor () throws -> any WordLooking = TranslatorFactory.makeWordLookup
    ) {
        self.makeTranslator = makeTranslator
        self.makeWordLookup = makeWordLookup
    }

    /// The backend accepts 1 to 500 characters (`TranslationRequest.java`).
    static let maxInputLength = 500

    /// How many characters the backend will count. Java counts UTF-16 units, so an emoji is 2.
    /// Counting the same way means the app never allows text the backend would reject.
    var inputLength: Int {
        trimmedInput.utf16.count
    }

    /// True when the text is too long to send. The input shows "Make it shorter."
    var isOverLimit: Bool {
        inputLength > Self.maxInputLength
    }

    /// True when the button should be enabled.
    var canTranslate: Bool {
        if case .loading = state { return false }
        if case .meaning = state { return false } // still waiting for the rest of the answer
        return !trimmedInput.isEmpty && !isOverLimit
    }

    private var trimmedInput: String {
        inputText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Handles `inputText` and updates `state`. One word (with or without an article, like "an apple") goes to the word lookup (a dictionary entry);
    /// anything longer goes to the backend as a sentence. Does nothing if `canTranslate` is false.
    func translate() async {
        guard canTranslate else { return }
        state = .loading
        if let word = CacheKeys.wordQuery(trimmedInput) {
            await lookUpWord(word)
        } else {
            await explainSentence()
        }
    }

    private func lookUpWord(_ word: String) async {
        var lookup: (any WordLooking)?
        do {
            let wordLookup = try makeWordLookup()
            lookup = wordLookup
            state = .loadedWord(try await wordLookup.lookUp(word))
        } catch {
            state = .failed(error.localizedDescription, spelling: spellingHelp(for: word, after: error, lookup: lookup))
        }
    }

    /// Close words to show under the error. Only when the backend says it is not an English word,
    /// or cannot be reached (`URLError`). The bundle was already checked before both.
    private func spellingHelp(for word: String, after error: Error, lookup: (any WordLooking)?) -> SpellingHelp? {
        guard let lookup else { return nil }
        var mayBeMisspelled = error is URLError
        if case .notAnEnglishWord = error as? WordLookupError { mayBeMisspelled = true }
        guard mayBeMisspelled else { return nil }

        let typedWord = CacheKeys.normalizeWord(word)
        let words = lookup.suggestions(for: typedWord)
        return words.isEmpty ? nil : SpellingHelp(typedWord: typedWord, suggestions: words)
    }

    /// The user tapped a "did you mean" word: put it in the input and look it up.
    func lookUp(suggestion word: String) async {
        inputText = word
        await translate()
    }

    /// The full word card for a word in a sentence result. Uses the bundled entry when there is one,
    /// otherwise the normal lookup (local cache, then backend). The sentence result stays on screen.
    func wordEntry(for word: SentenceWordInfo) async throws -> WordEntry {
        if let entry = word.entry { return entry }
        return try await makeWordLookup().lookUp(word.word)
    }

    /// Streams the answer so the meaning shows first (TASK-65). If the stream breaks before the full answer,
    /// the old non-streaming call is tried once. A clear "no" from the server (a 4xx, for example "not English")
    /// is not retried: asking again would give the same answer.
    private func explainWithFallback(
        _ sentence: String,
        language: TargetLanguage,
        translator: any Translating
    ) async throws -> Explanation {
        do {
            return try await translator.explainStreaming(sentence, translatingTo: language) { [weak self] meaning in
                self?.showMeaning(SentenceMeaning(sentence: sentence, meaning: meaning, language: language))
            }
        } catch let error as BackendError where error.isFinalAnswer {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled {
            throw error
        } catch {
            return try await translator.explain(sentence, translatingTo: language)
        }
    }

    /// Shows the meaning that arrived first. Only while the request is still running.
    private func showMeaning(_ meaning: SentenceMeaning) {
        switch state {
        case .loading, .meaning: state = .meaning(meaning)
        case .idle, .loaded, .loadedWord, .failed: break
        }
    }

    private func explainSentence() async {
        // Copy these first. The user can change the picker or the text while we wait.
        let sentence = trimmedInput
        let language = targetLanguage
        do {
            let translator = try makeTranslator()
            let explanation = try await explainWithFallback(sentence, language: language, translator: translator)
            // The bundled dictionary gives each word card its pill and chip. No dictionary is fine.
            let lookup = try? makeWordLookup()
            state = .loaded(SentenceResult(
                sentence: sentence,
                explanation: explanation,
                language: language,
                bundledEntry: { lookup?.bundledEntry(for: $0) }))
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
