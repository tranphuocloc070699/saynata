//
//  TranslateView.swift
//  EnglishTranslation
//

import SwiftUI

/// The main screen. Only puts the subviews together; each part lives in `Subviews/`.
struct TranslateView: View {
    @Environment(TranslationStore.self) private var store
    @Environment(SpeechPlayer.self) private var speechPlayer
    /// True while the text box has the keyboard (TASK-50).
    @FocusState private var isInputFocused: Bool

    var body: some View {
        @Bindable var store = store

        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                TranslateOptionsView(targetLanguage: $store.targetLanguage)
                    .simultaneousGesture(hideKeyboardTap)
                TranslateInputView(isFocused: $isInputFocused)
                TranslateResultView(
                    state: store.state,
                    targetLanguage: store.targetLanguage,
                    onSuggestionTap: { word in Task { await store.lookUp(suggestion: word) } },
                    loadWordEntry: { word in try await store.wordEntry(for: word) })
                    // At least this tall, so the empty space under a short result also hides the keyboard.
                    .frame(maxWidth: .infinity, minHeight: 400, alignment: .topLeading)
                    .contentShape(Rectangle())
                    .simultaneousGesture(hideKeyboardTap)
            }
            .padding()
        }
        // Drag the result to hide the keyboard.
        .scrollDismissesKeyboard(.interactively)
        // A new search or a new result replaces what is on screen, so the old speech must stop (TASK-46).
        .onChange(of: store.state.resultKey) { speechPlayer.stop() }
    }

    /// A tap outside the text box hides the keyboard. It is "simultaneous", so buttons, folded rows,
    /// speakers and text selection still get the same tap. It is not on the text box itself:
    /// a tap there must keep the keyboard.
    private var hideKeyboardTap: some Gesture {
        TapGesture().onEnded { isInputFocused = false }
    }
}

#Preview {
    TranslateView()
        .environment(TranslationStore())
        .environment(SpeechPlayer.silent())
}
