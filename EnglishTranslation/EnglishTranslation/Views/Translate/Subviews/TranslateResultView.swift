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

    var body: some View {
        switch state {
        case .idle:
            Text("Type a word or a sentence, then tap Explain.")
                .foregroundStyle(.secondary)
        case .loading:
            ProgressView()
        case let .loaded(explanation):
            TranslateExplanationView(explanation: explanation, targetLanguage: targetLanguage.title)
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
