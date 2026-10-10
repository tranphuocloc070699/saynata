//
//  TranslateExplanationView.swift
//  EnglishTranslation
//

import SwiftUI

/// Format A: Meaning, Words, then the translation at the bottom.
struct TranslateExplanationView: View {
    let explanation: Explanation
    let targetLanguage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            TitledSection(title: "Meaning") {
                Text(explanation.meaning)
            }

            if !explanation.words.isEmpty {
                TitledSection(title: "Words") {
                    ForEach(explanation.words) { word in
                        TranslateWordRow(word: word)
                    }
                }
            }

            TitledSection(title: targetLanguage) {
                Text(explanation.translation)
            }
        }
        .textSelection(.enabled)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    TranslateExplanationView(
        explanation: Explanation(
            meaning: "He took an old, flat silver watch out of his pocket.",
            words: [
                .init(word: "drew out", partOfSpeech: "phrasal verb", meaning: "took out (base form: draw out)"),
                .init(word: "old-fashioned", partOfSpeech: "adjective", meaning: "from an earlier time"),
            ],
            translation: "Anh ấy rút từ trong túi ra một chiếc đồng hồ bạc dẹt kiểu cũ."),
        targetLanguage: "Vietnamese"
    )
    .padding()
}
