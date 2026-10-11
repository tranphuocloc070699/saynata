//
//  TranslateView.swift
//  EnglishTranslation
//

import SwiftUI

/// The main screen. Only puts the subviews together; each part lives in `Subviews/`.
struct TranslateView: View {
    @Environment(TranslationStore.self) private var store

    var body: some View {
        @Bindable var store = store

        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                TranslateOptionsView(targetLanguage: $store.targetLanguage)
                TranslateInputView()
                TranslateResultView(state: store.state, targetLanguage: store.targetLanguage.title) { word in
                    Task { await store.lookUp(suggestion: word) }
                }
            }
            .padding()
        }
    }
}

#Preview {
    TranslateView()
        .environment(TranslationStore())
}
