//
//  TranslateOptionsView.swift
//  EnglishTranslation
//

import SwiftUI

/// The provider picker and the translation language picker.
struct TranslateOptionsView: View {
    @Binding var provider: Provider
    @Binding var targetLanguage: TargetLanguage

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Provider", selection: $provider) {
                ForEach(Provider.allCases) { provider in
                    Text(provider.title).tag(provider)
                }
            }
            .pickerStyle(.segmented)

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
    TranslateOptionsView(provider: .constant(.server), targetLanguage: .constant(.vietnamese))
        .padding()
}
