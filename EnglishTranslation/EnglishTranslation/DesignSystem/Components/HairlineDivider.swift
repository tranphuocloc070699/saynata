//
//  HairlineDivider.swift
//  EnglishTranslation
//

import SwiftUI

/// A thin line that is exactly one device pixel tall (1 / displayScale).
/// Use it instead of `Divider().overlay(color)`: that mixes the system divider with a
/// colored overlay, and while scrolling the two snap to pixels differently, so the line flickers.
struct HairlineDivider: View {
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        Rectangle()
            .fill(Theme.Colors.hairline)
            .frame(height: 1 / displayScale)
            .accessibilityHidden(true)
    }
}

#Preview {
    VStack(spacing: 16) {
        Text("Above")
        HairlineDivider()
        Text("Below")
    }
    .padding()
}
