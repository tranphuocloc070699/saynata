//
//  WordCardView.swift
//  EnglishTranslation
//

import SwiftUI

/// The full word card: word, IPA, then for each sense the meaning, examples and usage.
/// The Vietnamese row is last in each sense and closed by default (CLAUDE.md "Product rule").
/// Shown for a single-word input (TASK-19); previews use the sample words.
struct WordCardView: View {
    let entry: WordEntry
    /// Called when the listen button is tapped. Speech is not built yet.
    var onListen: () -> Void = {}

    /// Which folded row is open, for example "0-Vietnamese". Only one at a time (design rule 8).
    @State private var openRow = ""

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            header

            ForEach(Array(entry.senses.enumerated()), id: \.offset) { index, sense in
                if index > 0 {
                    HairlineDivider()
                }
                senseView(sense, index: index)
            }
        }
        .padding(Theme.Spacing.large)
        .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.card))
    }

    // MARK: - Parts

    private var header: some View {
        HStack(alignment: .center, spacing: Theme.Spacing.small) {
            VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                Text(entry.word)
                    .font(.largeTitle.weight(.semibold))
                    .foregroundStyle(Theme.Colors.textPrimary)
                if let ipa = entry.ipa {
                    Text(ipa)
                        .font(.callout)
                        .foregroundStyle(Theme.Colors.textSecondary)
                }
            }
            Spacer()
            IconButton(systemImage: "speaker.wave.2", label: "Listen", action: onListen)
        }
    }

    private func senseView(_ sense: Sense, index: Int) -> some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
            LevelChip(text: sense.chipText)

            MeaningBlock(meaning: sense.simpleDefinition, example: sense.examples.first)

            VStack(spacing: 0) {
                if sense.examples.count > 1 {
                    foldedRow("More examples", index: index, count: sense.examples.count - 1) {
                        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                            ForEach(sense.examples.dropFirst(), id: \.self) { example in
                                Text(example)
                                    .font(.system(.callout, design: .serif).italic())
                                    .foregroundStyle(Theme.Colors.textSecondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                if !sense.usage.isEmpty {
                    foldedRow("How to use it", index: index, count: sense.usage.count) {
                        WordUsageSection(usage: sense.usage)
                    }
                }
                if !sense.vietnamese.isEmpty {
                    foldedRow("Vietnamese", index: index) {
                        Text(sense.vietnamese)
                            .font(.body)
                            .foregroundStyle(Theme.Colors.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }

    /// One folded row. Opening one closes the others.
    private func foldedRow<Content: View>(
        _ title: String,
        index: Int,
        count: Int? = nil,
        @ViewBuilder content: () -> Content
    ) -> some View {
        let key = "\(index)-\(title)"
        return FoldedRow(
            title: title,
            count: count,
            isExpanded: Binding(
                get: { openRow == key },
                set: { openRow = $0 ? key : "" }
            ),
            content: content
        )
    }
}

#Preview("Normal word") {
    ScrollView { WordCardView(entry: .depend).padding() }
        .background(Theme.Colors.background)
}

#Preview("Usage labels") {
    ScrollView { WordCardView(entry: .get).padding() }
        .background(Theme.Colors.background)
}

#Preview("Many senses") {
    ScrollView { WordCardView(entry: .run).padding() }
        .background(Theme.Colors.background)
}

#Preview("Missing optional fields") {
    ScrollView { WordCardView(entry: .sparse).padding() }
        .background(Theme.Colors.background)
}
