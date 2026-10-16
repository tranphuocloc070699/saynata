//
//  TranslateInputView.swift
//  EnglishTranslation
//

import SwiftUI

/// The text box, the character counter and the Explain button.
struct TranslateInputView: View {
    @Environment(TranslationStore.self) private var store
    /// Owned by the screen, so a tap outside the box can hide the keyboard.
    var isFocused: FocusState<Bool>.Binding

    var body: some View {
        @Bindable var store = store

        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
            TextEditor(text: $store.inputText)
                .focused(isFocused)
                .frame(height: 140)
                .padding(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(.secondary.opacity(0.4)))

            HStack(spacing: Theme.Spacing.medium) {
                Spacer()
                CharacterCounter(count: store.inputLength, limit: TranslationStore.maxInputLength)
                IconButton(systemImage: "arrow.up", label: "Explain", isProminent: true) {
                    isFocused.wrappedValue = false
                    Task { await store.translate() }
                }
                .disabled(!store.canTranslate)
                .opacity(store.canTranslate ? 1 : 0.4)
            }
        }
    }
}
