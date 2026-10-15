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

/// "41 / 500". Over the limit it turns red and adds "Make it shorter." (words too, not only color).
struct CharacterCounter: View {
    let count: Int
    let limit: Int

    private var isOver: Bool { count > limit }

    var body: some View {
        HStack(spacing: Theme.Spacing.small) {
            if isOver {
                Text("Make it shorter.")
            }
            Text("\(count) / \(limit)")
                .monospacedDigit()
        }
        .font(.footnote)
        .foregroundStyle(isOver ? Theme.Colors.error : Theme.Colors.textSecondary)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    VStack(spacing: Theme.Spacing.large) {
        CharacterCounter(count: 41, limit: 500)
        CharacterCounter(count: 512, limit: 500)
    }
    .padding()
}
