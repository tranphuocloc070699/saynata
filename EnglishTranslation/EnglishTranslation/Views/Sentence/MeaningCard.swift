//
//  MeaningCard.swift
//  EnglishTranslation
//

import SwiftUI

/// The "Meaning" card of a sentence result: the simple English explanation (the hero), then a folded
/// row for the target language, closed by default (CLAUDE.md "Product rule").
/// The translation already comes from the backend, so unlike the word card it needs no on-device translation.
struct MeaningCard: View {
    let meaning: String
    let translation: String
    /// The language the translation was made in. It titles the folded row.
    let language: TargetLanguage

    @State private var isTranslationOpen = false

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            Text("Meaning")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.Colors.textSecondary)
                .textCase(.uppercase)

            Text(meaning)
                .font(.system(.body, design: .serif))
                .foregroundStyle(Theme.Colors.textPrimary)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)

            FoldedRow(title: language.title, isExpanded: $isTranslationOpen) {
                Text(translation)
                    .font(.body)
                    .foregroundStyle(Theme.Colors.textPrimary)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .cardSurface()
    }
}

#Preview {
    MeaningCard(
        meaning: "The fog made the edges of everything hard to see clearly.",
        translation: "Màn sương làm nhòe đi các đường biên của thế giới.",
        language: .vietnamese)
        .padding()
        .background(Theme.Colors.background)
}
