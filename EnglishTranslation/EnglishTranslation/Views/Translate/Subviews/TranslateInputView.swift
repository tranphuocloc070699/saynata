//
//  TranslateInputView.swift
//  EnglishTranslation
//

import SwiftUI

/// The composer card (TASK-97): the text on top; below it the target-language chip on the left,
/// the character counter (only near the limit) and the round Explain button on the right.
struct TranslateInputView: View {
    @Environment(TranslationStore.self) private var store
    /// Owned by the screen, so a tap outside the box can hide the keyboard.
    var isFocused: FocusState<Bool>.Binding

    /// The counter stays hidden until the text is close to the limit.
    static let counterStart = 450

    var body: some View {
        @Bindable var store = store

        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
            // 2 lines when empty, grows to 6, then scrolls.
            TextField("Type a word or a sentence", text: $store.inputText, axis: .vertical)
                .font(.body)
                .foregroundStyle(Theme.Colors.textPrimary)
                .lineLimit(2...6)
                .focused(isFocused)

            HStack(spacing: Theme.Spacing.medium) {
                languageChip(selection: $store.targetLanguage)
                Spacer()
                if store.inputLength >= Self.counterStart {
                    CharacterCounter(count: store.inputLength, limit: TranslationStore.maxInputLength)
                }
                IconButton(systemImage: "arrow.up", label: "Explain", isProminent: true) {
                    isFocused.wrappedValue = false
                    Task { await store.translate() }
                }
                .disabled(!store.canTranslate)
                .opacity(store.canTranslate ? 1 : 0.4)
            }
        }
        .padding(Theme.Spacing.large)
        .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.card))
        .overlay {
            // Accent only while the box has the keyboard (design-system rule 3: focus).
            RoundedRectangle(cornerRadius: Theme.Radius.card)
                .strokeBorder(isFocused.wrappedValue ? Theme.Colors.accent : Theme.Colors.hairline, lineWidth: 1)
        }
    }

    /// The target-language picker, drawn as a small pill (a menu).
    private func languageChip(selection: Binding<TargetLanguage>) -> some View {
        Menu {
            Picker("Translate into", selection: selection) {
                ForEach(TargetLanguage.allCases) { language in
                    Text(language.title).tag(language)
                }
            }
        } label: {
            HStack(spacing: Theme.Spacing.xSmall) {
                Text(selection.wrappedValue.title)
                Image(systemName: "chevron.down")
                    .imageScale(.small)
            }
            .font(.footnote.weight(.medium))
            .foregroundStyle(Theme.Colors.textSecondary)
            .padding(.horizontal, Theme.Spacing.medium)
            .padding(.vertical, Theme.Spacing.small)
            .background(Theme.Colors.chipFill, in: Capsule())
        }
        .accessibilityLabel("Translate into")
        .accessibilityValue(selection.wrappedValue.title)
    }
}

#Preview {
    @Previewable @FocusState var focused: Bool
    TranslateInputView(isFocused: $focused)
        .padding()
        .background(Theme.Colors.background)
        .environment(TranslationStore())
}
