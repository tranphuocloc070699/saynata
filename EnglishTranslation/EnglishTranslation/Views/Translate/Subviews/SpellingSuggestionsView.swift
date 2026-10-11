//
//  SpellingSuggestionsView.swift
//  EnglishTranslation
//

import SwiftUI

/// "No word "runj". Did you mean:" and up to 3 tappable words (chips). Shown under a lookup error.
struct SpellingSuggestionsView: View {
    let help: SpellingHelp
    let onTap: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            Text("No word \"\(help.typedWord)\". Did you mean:")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            HStack(spacing: Theme.Spacing.small) {
                ForEach(help.suggestions, id: \.self) { word in
                    Button { onTap(word) } label: {
                        // Same look as `LevelChip`, with a 44 pt tap area.
                        Text(word)
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(Theme.Colors.accent)
                            .padding(.horizontal, Theme.Spacing.medium)
                            .frame(minHeight: 44)
                            .background(Theme.Colors.chipFill, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Look up \(word)")
                }
            }
        }
    }
}

#Preview {
    SpellingSuggestionsView(help: SpellingHelp(typedWord: "runj", suggestions: ["run", "rung"]), onTap: { _ in })
        .padding()
}
