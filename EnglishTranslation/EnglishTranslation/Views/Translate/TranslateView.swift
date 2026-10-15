//
//  TranslateView.swift
//  EnglishTranslation
//

import SwiftUI

/// The main screen. Only puts the subviews together; each part lives in `Subviews/`.
struct TranslateView: View {
    @Environment(TranslationStore.self) private var store
    @Environment(SpeechPlayer.self) private var speechPlayer

    var body: some View {
        @Bindable var store = store

        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                TranslateOptionsView(targetLanguage: $store.targetLanguage)
                TranslateInputView()
                TranslateResultView(
                    state: store.state,
                    targetLanguage: store.targetLanguage,
                    onSuggestionTap: { word in Task { await store.lookUp(suggestion: word) } },
                    loadWordEntry: { word in try await store.wordEntry(for: word) })
            }
            .padding()
        }
        // A new search or a new result replaces what is on screen, so the old speech must stop (TASK-46).
        .onChange(of: store.state.resultKey) { speechPlayer.stop() }
    }
}

#Preview {
    TranslateView()
        .environment(TranslationStore())
        .environment(SpeechPlayer.silent())
}
