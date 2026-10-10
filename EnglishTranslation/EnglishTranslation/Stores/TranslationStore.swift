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

    /// - Parameter makeTranslator: Builds the translator for a provider on each request, so a
    ///   missing key shows as a `.failed` state instead of crashing at launch.
    init(
        makeTranslator: @escaping @MainActor (Provider) throws -> any Translating = TranslatorFactory.makeTranslator(for:)
    ) {
        self.makeTranslator = makeTranslator
    }

    /// True when the button should be enabled.
    var canTranslate: Bool {
        if case .loading = state { return false }
        return !trimmedInput.isEmpty
    }

    private var trimmedInput: String {
        inputText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Explains `inputText` with `provider`, and updates `state`. Does nothing if `canTranslate` is false.
    func translate() async {
        guard canTranslate else { return }
        state = .loading
        do {
            let translator = try makeTranslator(provider)
            let explanation = try await translator.explain(trimmedInput, translatingTo: targetLanguage)
            state = .loaded(explanation)
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
