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
    @Environment(SpeechPlayer.self) private var speechPlayer

    private var displayedWord: String { entry.matchedForm?.text ?? entry.word }
    private var speechID: String { "word-\(displayedWord)" }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            HStack(alignment: .center, spacing: Theme.Spacing.small) {
                HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.small) {
                    Text(displayedWord)
                        .font(.headline)
                        .foregroundStyle(Theme.Colors.textPrimary)
                        .speechHighlight(isPlaying: speechPlayer.currentID == speechID)
                    if let ipa = entry.ipa {
                        Text(ipa)
                            .font(.subheadline)
                            .foregroundStyle(Theme.Colors.textSecondary)
                    }
                }
                Spacer()
                IconButton(
                    systemImage: speechPlayer.currentID == speechID ? "stop.fill" : "speaker.wave.2",
                    label: speechPlayer.currentID == speechID ? "Stop" : "Listen to the word"
                ) {
                    if speechPlayer.currentID == speechID {
                        speechPlayer.stop()
                    } else {
                        speechPlayer.speak(displayedWord, id: speechID)
                    }
                }
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
    .environment(SpeechPlayer.silent())
}
