//
//  TranslateOptionsView.swift
//  EnglishTranslation
//

import SwiftUI

/// The translation language picker, and the Settings button at the right.
struct TranslateOptionsView: View {
    @Binding var targetLanguage: TargetLanguage
    @State private var isSettingsOpen = false
    @State private var isHistoryOpen = false
    @Environment(TranslationStore.self) private var store

    var body: some View {
        HStack {
            Picker("Translate into", selection: $targetLanguage) {
                ForEach(TargetLanguage.allCases) { language in
                    Text(language.title).tag(language)
                }
            }
            .pickerStyle(.menu)
            Spacer()
            if store.history != nil {
                IconButton(systemImage: "clock", label: "History and Liked") { isHistoryOpen = true }
            }
            IconButton(systemImage: "gearshape", label: "Settings") { isSettingsOpen = true }
        }
        .sheet(isPresented: $isSettingsOpen) {
            SettingsView(targetLanguage: targetLanguage)
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
    TranslateOptionsView(targetLanguage: .constant(.vietnamese))
        .padding()
        .environment(TranslationStore())
}
