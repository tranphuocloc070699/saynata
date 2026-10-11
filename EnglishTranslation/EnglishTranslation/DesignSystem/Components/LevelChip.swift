//
//  LevelChip.swift
//  EnglishTranslation
//

import SwiftUI

/// A small pill with the word type and CEFR level, for example "adj · B2".
struct LevelChip: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.footnote.weight(.medium))
            .foregroundStyle(Theme.Colors.accent)
            .padding(.horizontal, Theme.Spacing.small)
            .padding(.vertical, Theme.Spacing.xSmall)
            .background(Theme.Colors.chipFill, in: Capsule())
    }
}

#Preview {
    LevelChip(text: "adj · B2")
        .padding()
}
