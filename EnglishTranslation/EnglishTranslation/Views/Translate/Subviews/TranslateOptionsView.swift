//
//  TranslateOptionsView.swift
//  EnglishTranslation
//

import SwiftUI

/// The translation language picker.
struct TranslateOptionsView: View {
    @Binding var targetLanguage: TargetLanguage

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Translate into", selection: $targetLanguage) {
                ForEach(TargetLanguage.allCases) { language in
                    Text(language.title).tag(language)
                }
            }
            .pickerStyle(.menu)
        }
    }
}

#Preview {
    TranslateOptionsView(targetLanguage: .constant(.vietnamese))
        .padding()
}
