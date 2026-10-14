//
//  WordCardCompactView.swift
//  EnglishTranslation
//

import SwiftUI

/// The small word card for the word list in a sentence result.
/// Shows the first sense only: word, IPA, the listen button, then the numbered title
/// (number, simple meaning, level chip). Vietnamese is a closed row at the bottom.
struct WordCardCompactView: View {
    let entry: WordEntry
    var targetLanguage: TargetLanguage = .vietnamese

    /// Starts the way "Show translation" in Settings says.
    @State private var isLanguageRowOpen = TranslationRowPreference().startsOpen

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            HStack(alignment: .center, spacing: Theme.Spacing.small) {
                HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.small) {
                    Text(entry.word)
                        .font(.headline)
                        .foregroundStyle(Theme.Colors.textPrimary)
                        .speechHighlight(.word(of: entry))
                    if let ipa = entry.ipa {
                        Text(ipa)
                            .font(.subheadline)
                            .foregroundStyle(Theme.Colors.textSecondary)
                    }
                }
                Spacer()
                SpeakerButton(part: .word(of: entry))
            }

            if let sense = entry.senses.first {
                SenseTitleRow(number: 1, sense: sense)

                if sense.hasTargetLanguageRow(for: targetLanguage) {
                    FoldedRow(title: targetLanguage.title, isExpanded: $isLanguageRowOpen) {
                        TargetLanguageRowContent(sense: sense, language: targetLanguage)
                    }
                    .onChange(of: isLanguageRowOpen) { _, isOpen in
                        TranslationRowPreference().rowChanged(isOpen: isOpen)
                    }
                }
            }
        }
        .padding(Theme.Spacing.large)
        .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.card))
        .overlay {
            RoundedRectangle(cornerRadius: Theme.Radius.card).strokeBorder(Theme.Colors.hairline, lineWidth: 1)
        }
    }
}

#Preview("Compact") {
    VStack(spacing: Theme.Spacing.medium) {
        WordCardCompactView(entry: .depend)
        WordCardCompactView(entry: .sparse)
    }
    .padding()
    .background(Theme.Colors.background)
}
