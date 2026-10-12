//
//  TranslateResultView.swift
//  EnglishTranslation
//

import SwiftUI

/// Shows one `TranslationState`.
struct TranslateResultView: View {
    let state: TranslationState
    let targetLanguage: TargetLanguage
    /// Called when the user taps a "did you mean" word.
    var onSuggestionTap: (String) -> Void = { _ in }
    /// Gets the full word card for a word the user tapped in a sentence result.
    var loadWordEntry: (SentenceWordInfo) async throws -> WordEntry = { word in
        guard let entry = word.entry else { throw WordLookupError.notOneWord }
        return entry
    }

    var body: some View {
        switch state {
        case .idle:
            Text("Type a word or a sentence, then tap Explain.")
                .foregroundStyle(.secondary)
        case .loading:
            ProgressView()
        case let .loaded(result):
            // The language comes from the result, not the live picker (TASK-14).
            SentenceResultView(result: result, loadWordEntry: loadWordEntry)
        case let .loadedWord(entry):
            WordCardView(entry: entry, targetLanguage: targetLanguage)
        case let .failed(message, spelling):
            VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
                Text(message).foregroundStyle(.red)
                if let spelling {
                    SpellingSuggestionsView(help: spelling, onTap: onSuggestionTap)
                }
            }
        }
    }
}

#Preview {
    TranslateResultView(state: .failed("The server answered with status 401."), targetLanguage: .vietnamese)
        .padding()
}
