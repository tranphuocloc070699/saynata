//
//  WordUsageSection.swift
//  EnglishTranslation
//

import SwiftUI

/// The content of the "Grammar" row: usage items in groups (Patterns, Collocations,
/// Phrases and idioms). Each group has a small heading and a bullet list: the pattern in
/// medium weight, its example after it. Empty groups are not shown.
struct WordUsageSection: View {
    let groups: [UsageGroup]

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            ForEach(groups, id: \.title) { group in
                VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                    Text(group.title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.Colors.textSecondary)
                        .textCase(.uppercase)
                        .accessibilityAddTraits(.isHeader)

                    ForEach(Array(group.items.enumerated()), id: \.offset) { _, item in
                        HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.small) {
                            Text("•")
                                .foregroundStyle(Theme.Colors.textMuted)
                                .accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.pattern)
                                    .font(.body.weight(.medium))
                                    .foregroundStyle(Theme.Colors.textPrimary)
                                Text(item.example)
                                    .font(.system(.callout, design: .serif).italic())
                                    .foregroundStyle(Theme.Colors.textSecondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    WordUsageSection(groups: WordEntry.get.senses[0].usageGroups)
        .padding()
}
