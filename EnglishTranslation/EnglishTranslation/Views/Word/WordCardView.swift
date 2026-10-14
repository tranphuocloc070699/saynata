//
//  WordCardView.swift
//  EnglishTranslation
//

import SwiftUI

/// The full word card (word card v2, TASK-36): the headword, then one card per sense.
/// A sense card has a numbered title and three folded rows: Examples, Grammar, and the picked language
/// (Vietnamese by default). The language row is last and closed by default (CLAUDE.md "Product rule").
/// Shown for a single-word input (TASK-19); previews use the sample words.
struct WordCardView: View {
    let entry: WordEntry
    /// The picked language. It titles the last row (TASK-40).
    var targetLanguage: TargetLanguage = .vietnamese

    /// Which folded row is open, for example "0-Vietnamese". Only one at a time per card (design rule 8).
    @State private var openRow = ""
    /// Which senses have their target-language row open (by index). Starts as "Show translation" in Settings says.
    /// Kept apart from `openRow`, so "Always open" can open the row on every sense.
    @State private var openTargetRows: Set<Int>

    init(entry: WordEntry, targetLanguage: TargetLanguage = .vietnamese) {
        self.entry = entry
        self.targetLanguage = targetLanguage
        let startsOpen = TranslationRowPreference().startsOpen
        _openTargetRows = State(initialValue: startsOpen ? Set(entry.senses.indices) : [])
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            header

            ForEach(Array(entry.senses.enumerated()), id: \.offset) { index, sense in
                senseCard(sense, index: index)
            }
        }
    }

    // MARK: - Parts

    /// The word as typed (a form like "ran" stays "ran"). With a form, the pill shows the base word.
    private var header: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.small) {
            VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                Text(SpeechPart.word(of: entry).text)
                    .font(.largeTitle.weight(.semibold))
                    .foregroundStyle(Theme.Colors.textPrimary)
                    .speechHighlight(.word(of: entry))
                if let pill = entry.formPillText {
                    FormPill(text: pill)
                } else if let ipa = entry.ipa {
                    Text(ipa)
                        .font(.callout)
                        .foregroundStyle(Theme.Colors.textSecondary)
                }
            }
            Spacer()
            SpeakerButton(part: .word(of: entry))
        }
    }

    private func senseCard(_ sense: Sense, index: Int) -> some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            SenseTitleRow(
                number: index + 1, sense: sense,
                speech: .explanation(of: sense, senseIndex: index, in: entry))

            VStack(spacing: 0) {
                if !sense.examples.isEmpty {
                    foldedRow("Examples", index: index) {
                        WordExamplesSection(entry: entry, senseIndex: index)
                    }
                }
                if !sense.usage.isEmpty {
                    foldedRow("Grammar", index: index) {
                        WordUsageSection(groups: sense.usageGroups)
                    }
                }
                if sense.hasTargetLanguageRow(for: targetLanguage) {
                    FoldedRow(
                        title: targetLanguage.title,
                        isExpanded: Binding(
                            get: { openTargetRows.contains(index) },
                            set: { isOpen in
                                if isOpen { openTargetRows.insert(index) } else { openTargetRows.remove(index) }
                                TranslationRowPreference().rowChanged(isOpen: isOpen)
                            })
                    ) {
                        TargetLanguageRowContent(sense: sense, language: targetLanguage)
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

    /// One folded row, closed by default. Opening one closes the others.
    private func foldedRow<Content: View>(
        _ title: String,
        index: Int,
        @ViewBuilder content: () -> Content
    ) -> some View {
        let key = "\(index)-\(title)"
        return FoldedRow(
            title: title,
            isExpanded: Binding(
                get: { openRow == key },
                set: { openRow = $0 ? key : "" }
            ),
            content: content
        )
    }
}

#Preview("Form: ran") {
    ScrollView { WordCardView(entry: .ran).padding() }
        .background(Theme.Colors.background)
}

#Preview("Two senses: apple") {
    ScrollView { WordCardView(entry: .apple).padding() }
        .background(Theme.Colors.background)
}

#Preview("Usage in all groups: get") {
    ScrollView { WordCardView(entry: .get).padding() }
        .background(Theme.Colors.background)
}

#Preview("Sense with no usage") {
    ScrollView { WordCardView(entry: .run).padding() }
        .background(Theme.Colors.background)
}

#Preview("Old data: string examples") {
    ScrollView { WordCardView(entry: .oldData).padding() }
        .background(Theme.Colors.background)
}

#Preview("Missing optional fields") {
    ScrollView { WordCardView(entry: .sparse).padding() }
        .background(Theme.Colors.background)
}
