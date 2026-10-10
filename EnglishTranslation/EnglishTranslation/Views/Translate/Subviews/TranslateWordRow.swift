//
//  TranslateWordRow.swift
//  EnglishTranslation
//

import SwiftUI

/// "drew out (phrasal verb) – took out, often slowly"
struct TranslateWordRow: View {
    let word: Explanation.Word

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(word.word).bold()
                Text(word.partOfSpeech)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(.secondary.opacity(0.15), in: Capsule())
            }
            Text(word.meaning)
                .foregroundStyle(.secondary)
        }
    }
}
