//
//  CardSurface.swift
//  EnglishTranslation
//

import SwiftUI

extension View {
    /// The word-card look: padding, surface color, hairline border, card radius.
    /// The sentence cards use it. (The word cards in `Views/Word/` still draw the same thing by hand.)
    func cardSurface() -> some View {
        self
            .padding(Theme.Spacing.large)
            .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.card))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.card).strokeBorder(Theme.Colors.hairline, lineWidth: 1)
            }
    }
}

#Preview {
    Text("A card")
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardSurface()
        .padding()
        .background(Theme.Colors.background)
}
