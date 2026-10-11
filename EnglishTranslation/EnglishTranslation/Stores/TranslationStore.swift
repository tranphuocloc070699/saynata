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
        return !trimmedInput.isEmpty && !isOverLimit
    }

    private var trimmedInput: String {
        inputText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Handles `inputText` and updates `state`. One token goes to the word lookup (a dictionary entry);
    /// more than one goes to the backend as a sentence. Does nothing if `canTranslate` is false.
    func translate() async {
        guard canTranslate else { return }
        state = .loading
        if CacheKeys.isSingleToken(trimmedInput) {
            await lookUpWord()
        } else {
            await explainSentence()
        }
    }

    private func lookUpWord() async {
        var lookup: (any WordLooking)?
        do {
            let wordLookup = try makeWordLookup()
            lookup = wordLookup
            state = .loadedWord(try await wordLookup.lookUp(trimmedInput))
        } catch {
            state = .failed(error.localizedDescription, spelling: spellingHelp(after: error, lookup: lookup))
        }
    }

    /// Close words to show under the error. Only when the backend says it is not an English word,
    /// or cannot be reached (`URLError`). The bundle was already checked before both.
    private func spellingHelp(after error: Error, lookup: (any WordLooking)?) -> SpellingHelp? {
        guard let lookup else { return nil }
        var mayBeMisspelled = error is URLError
        if case .notAnEnglishWord = error as? WordLookupError { mayBeMisspelled = true }
        guard mayBeMisspelled else { return nil }

        let typedWord = CacheKeys.normalizeWord(CacheKeys.trimmingWordPunctuation(trimmedInput))
        let words = lookup.suggestions(for: typedWord)
        return words.isEmpty ? nil : SpellingHelp(typedWord: typedWord, suggestions: words)
    }

    /// The user tapped a "did you mean" word: put it in the input and look it up.
    func lookUp(suggestion word: String) async {
        inputText = word
        await translate()
    }

    private func explainSentence() async {
        // Copy these first. The user can change the picker or the text while we wait.
        let sentence = trimmedInput
        let language = targetLanguage
        do {
            let translator = try makeTranslator()
            let explanation = try await translator.explain(sentence, translatingTo: language)
            state = .loaded(SentenceResult(sentence: sentence, explanation: explanation, language: language))
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
