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
    /// Called when the listen button is tapped. Speech is not built yet.
    /// The picked language. It titles the last row (TASK-40).
    var targetLanguage: TargetLanguage = .vietnamese
    var onListen: () -> Void = {}
    /// False when the caller draws its own header (the share sheet puts three buttons beside the word).
    var showsHeader = true

    /// Which folded row is open, for example "0-Vietnamese". Only one at a time per card (design rule 8).
    @State private var openRow = ""
    /// Which senses have their target-language row open (by index). Starts as "Show translation" in Settings says.
    /// Kept apart from `openRow`, so "Always open" can open the row on every sense.
    @State private var openTargetRows: Set<Int>

    init(
        entry: WordEntry, targetLanguage: TargetLanguage = .vietnamese, showsHeader: Bool = true,
        onListen: @escaping () -> Void = {}
    ) {
        self.entry = entry
        self.targetLanguage = targetLanguage
        self.showsHeader = showsHeader
        self.onListen = onListen
        let startsOpen = TranslationRowPreference().startsOpen
        _openTargetRows = State(initialValue: startsOpen ? Set(entry.senses.indices) : [])
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            if showsHeader {
                header
            }

            ForEach(Array(entry.senses.enumerated()), id: \.offset) { index, sense in
                senseCard(sense, index: index)
            }
        }
    }

    // MARK: - Parts

    private var header: some View {
        WordHeaderView(entry: entry) {
            IconButton(systemImage: "speaker.wave.2", label: "Listen", action: onListen)
        }
    }

    private func senseCard(_ sense: Sense, index: Int) -> some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            SenseTitleRow(number: index + 1, sense: sense)

            if let imageUrl = sense.imageUrl {
                SenseMediaView(path: imageUrl)
                    .padding(.top, Theme.Spacing.xSmall)
            }

            VStack(spacing: 0) {
                if !sense.examples.isEmpty {
                    foldedRow("Examples", index: index) {
                        WordExamplesSection(examples: sense.examples)
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
