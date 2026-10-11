//
//  SenseTitleRow.swift
//  EnglishTranslation
//

import SwiftUI

/// The title of a sense card: a number in a circle, the simple explanation, and the level chip.
/// Used by the full card and the compact card.
struct SenseTitleRow: View {
    let number: Int
    let sense: Sense

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.medium) {
            Text("\(number)")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.Colors.textPrimary)
                .frame(width: 24, height: 24)
                .background(Theme.Colors.chipFill, in: Circle())
                .accessibilityLabel("Meaning \(number)")

            Text(sense.simpleDefinition)
                .font(.system(.body, design: .serif))
                .foregroundStyle(Theme.Colors.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .textSelection(.enabled)

            LevelChip(text: sense.chipText)
                .fixedSize()
        }
    }
}

#Preview {
    SenseTitleRow(number: 1, sense: WordEntry.depend.senses[0])
        .padding()
}
