//
//  TranslateOptionsView.swift
//  EnglishTranslation
//

import SwiftUI

/// The top row (TASK-97): the menu button at the left (opens Settings) and the History button at the right.
/// The language picker now lives in the composer card (`TranslateInputView`).
struct TranslateOptionsView: View {
    @State private var isSettingsOpen = false
    @State private var isHistoryOpen = false
    @Environment(TranslationStore.self) private var store

    var body: some View {
        HStack {
            IconButton(systemImage: "line.3.horizontal", label: "Settings") { isSettingsOpen = true }
            Spacer()
            if store.history != nil {
                IconButton(systemImage: "clock", label: "History and Liked") { isHistoryOpen = true }
            }
        }
        .sheet(isPresented: $isSettingsOpen) {
            SettingsView(targetLanguage: store.targetLanguage)
        }
        // A tapped row shows that result again: the text goes in the input and the normal search runs,
        // so an answer we already have costs nothing (TASK-93.2).
        .sheet(isPresented: $isHistoryOpen) {
            if let history = store.history {
                HistorySheet(history: history) { text in
                    store.inputText = text
                    Task { await store.translate() }
                }
            }
        }
    }
}

#Preview {
    TranslateOptionsView()
        .padding()
        .environment(TranslationStore())
}
