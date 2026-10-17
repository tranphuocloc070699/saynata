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
    /// Nil while the answer is still streaming: the folded row is left out until it is there.
    let translation: String?
    /// The language the translation was made in. It titles the folded row.
    let language: TargetLanguage

    /// Starts the way "Show translation" in Settings says.
    @State private var isTranslationOpen = TranslationRowPreference().startsOpen

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            Text("Meaning")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.Colors.textSecondary)
                .textCase(.uppercase)

            if meaning.isEmpty {
                // Nothing written yet (TASK-74): a spinner where the meaning will appear.
                ProgressView()
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text(meaning)
                    .font(.system(.body, design: .serif))
                    .foregroundStyle(Theme.Colors.textPrimary)
                    .textSelection(.enabled)
                    // The meaning grows word by word while it streams (TASK-74). Without this, a selectable Text
                    // could keep the height of its first line and end in "…" (seen on the developer's iPhone).
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            // No translation yet (the answer is still streaming): the row appears when the full result arrives.
            if let translation {
                FoldedRow(title: language.title, isExpanded: $isTranslationOpen) {
                    Text(translation)
                        .font(.body)
                        .foregroundStyle(Theme.Colors.textPrimary)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .onChange(of: isTranslationOpen) { _, isOpen in
                    TranslationRowPreference().rowChanged(isOpen: isOpen)
                }
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
