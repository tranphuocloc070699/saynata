//
//  WordUsageSection.swift
//  EnglishTranslation
//

import SwiftUI

/// The content of the "How to use it" row: each pattern on one line, its example under it.
/// Collocations, phrases and idioms get a small label first; a plain pattern has none.
/// A separate subview so the design is easy to change.
struct WordUsageSection: View {
    let usage: [Usage]

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
            ForEach(usage, id: \.self) { item in
                VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                    if let label = item.kind.label {
                        Text(label)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.Colors.textSecondary)
                            .textCase(.uppercase)
                    }
                    Text(item.pattern)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Theme.Colors.textPrimary)
                    Text(item.example)
                        .font(.system(.callout, design: .serif).italic())
                        .foregroundStyle(Theme.Colors.textSecondary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    WordUsageSection(usage: WordEntry.get.senses[0].usage)
        .padding()
}
