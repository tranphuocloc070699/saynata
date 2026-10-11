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
    /// The service used on the next request.
    var provider: Provider = .server
    private(set) var state: TranslationState = .idle

    private let makeTranslator: @MainActor (Provider) throws -> any Translating
    private let makeWordLookup: @MainActor () throws -> any WordLooking

    /// - Parameter makeTranslator: Builds the translator for a provider on each request, so a
    ///   missing key shows as a `.failed` state instead of crashing at launch.
    init(
        makeTranslator: @escaping @MainActor (Provider) throws -> any Translating = TranslatorFactory.makeTranslator(for:),
        makeWordLookup: @escaping @MainActor () throws -> any WordLooking = TranslatorFactory.makeWordLookup
    ) {
        self.makeTranslator = makeTranslator
        self.makeWordLookup = makeWordLookup
    }

    /// True when the button should be enabled.
    var canTranslate: Bool {
        if case .loading = state { return false }
        return !trimmedInput.isEmpty
    }

    private var trimmedInput: String {
        inputText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Handles `inputText` and updates `state`. One token goes to the word lookup (a dictionary entry);
    /// more than one goes to `provider` as a sentence. Does nothing if `canTranslate` is false.
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
        do {
            let translator = try makeTranslator(provider)
            let explanation = try await translator.explain(trimmedInput, translatingTo: targetLanguage)
            state = .loaded(explanation)
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
