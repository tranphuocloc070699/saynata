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
                TranslateOptionsView(provider: $store.provider, targetLanguage: $store.targetLanguage)
                TranslateInputView()
                TranslateResultView(state: store.state, targetLanguage: store.targetLanguage.title)
            }
            .padding()
        }
    }
}

#Preview {
    TranslateView()
        .environment(TranslationStore())
}
