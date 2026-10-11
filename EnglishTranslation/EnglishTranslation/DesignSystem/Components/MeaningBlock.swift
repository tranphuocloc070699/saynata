//
//  MeaningBlock.swift
//  EnglishTranslation
//

import SwiftUI

/// The hero of every result: the simple English meaning, in serif, on a tinted block.
/// Uses `.body` (17 pt) so it grows with Dynamic Type.
struct MeaningBlock: View {
    let meaning: String
    /// One example sentence. More examples go in a `FoldedRow`.
    var example: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            Text("Meaning")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.Colors.textSecondary)
                .textCase(.uppercase)

            Text(meaning)
                .font(.system(.body, design: .serif))
                .foregroundStyle(Theme.Colors.textPrimary)

            if let example {
                Text(example)
                    .font(.system(.callout, design: .serif).italic())
                    .foregroundStyle(Theme.Colors.textSecondary)
            }
        }
        .textSelection(.enabled)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Theme.Spacing.large)
        .background(Theme.Colors.meaningBlock, in: RoundedRectangle(cornerRadius: Theme.Radius.block))
    }
}

#Preview {
    MeaningBlock(
        meaning: "Very careful to do things well and correctly.",
        example: "She is thorough in her work."
    )
    .padding()
}
