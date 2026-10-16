//
//  CharacterCounter.swift
//  EnglishTranslation
//

import SwiftUI

/// "41 / 500". Over the limit it turns red and adds "Make it shorter." (words too, not only color).
struct CharacterCounter: View {
    let count: Int
    let limit: Int

    private var isOver: Bool { count > limit }

    var body: some View {
        HStack(spacing: Theme.Spacing.small) {
            if isOver {
                Text("Make it shorter.")
            }
            Text("\(count) / \(limit)")
                .monospacedDigit()
        }
        .font(.footnote)
        .foregroundStyle(isOver ? Theme.Colors.error : Theme.Colors.textSecondary)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    VStack(spacing: Theme.Spacing.large) {
        CharacterCounter(count: 41, limit: 500)
        CharacterCounter(count: 512, limit: 500)
    }
    .padding()
}
