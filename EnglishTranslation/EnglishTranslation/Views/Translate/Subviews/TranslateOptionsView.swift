//
//  TranslateOptionsView.swift
//  EnglishTranslation
//

import SwiftUI

/// The translation language picker, and the Settings button at the right.
struct TranslateOptionsView: View {
    @Binding var targetLanguage: TargetLanguage
    @State private var isSettingsOpen = false

    var body: some View {
        HStack {
            Picker("Translate into", selection: $targetLanguage) {
                ForEach(TargetLanguage.allCases) { language in
                    Text(language.title).tag(language)
                }
            }
            .pickerStyle(.menu)
            Spacer()
            IconButton(systemImage: "gearshape", label: "Settings") { isSettingsOpen = true }
        }
        .sheet(isPresented: $isSettingsOpen) {
            SettingsView(targetLanguage: targetLanguage)
        }
    }
}

#Preview {
    TranslateOptionsView(targetLanguage: .constant(.vietnamese))
        .padding()
}
