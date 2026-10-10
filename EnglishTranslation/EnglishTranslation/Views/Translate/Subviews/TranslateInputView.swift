//
//  TranslateInputView.swift
//  EnglishTranslation
//

import SwiftUI

/// The text box and the Explain button.
struct TranslateInputView: View {
    @Environment(TranslationStore.self) private var store

    var body: some View {
        @Bindable var store = store

        VStack(alignment: .leading, spacing: 16) {
            TextEditor(text: $store.inputText)
                .frame(height: 140)
                .padding(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(.secondary.opacity(0.4)))

            Button("Explain") {
                Task { await store.translate() }
            }
            .buttonStyle(.borderedProminent)
            .disabled(!store.canTranslate)
        }
    }
}

#Preview {
    TranslateInputView()
        .environment(TranslationStore())
        .padding()
}
