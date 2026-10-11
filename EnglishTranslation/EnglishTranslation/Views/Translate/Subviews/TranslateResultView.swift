//
//  TranslateResultView.swift
//  EnglishTranslation
//

import SwiftUI

/// Shows one `TranslationState`.
struct TranslateResultView: View {
    let state: TranslationState
    let targetLanguage: String

    var body: some View {
        switch state {
        case .idle:
            Text("Type a word or a sentence, then tap Explain.")
                .foregroundStyle(.secondary)
        case .loading:
            ProgressView()
        case let .loaded(explanation):
            TranslateExplanationView(explanation: explanation, targetLanguage: targetLanguage)
        case let .loadedWord(entry):
            WordCardView(entry: entry)
        case let .failed(message):
            Text(message).foregroundStyle(.red)
        }
    }
}

#Preview {
    TranslateResultView(state: .failed("The server answered with status 401."), targetLanguage: "Vietnamese")
        .padding()
}
