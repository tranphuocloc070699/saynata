//
//  Theme+Snippet.swift
//  EnglishTranslation
//

import SwiftUI

extension Theme {
    /// The same colors as `Theme.Colors`, written as numbers, for the Shortcuts card of "Explain copied text" (TASK-87).
    ///
    /// When Shortcuts draws an App Intent card, the app cannot read its asset catalog ("No color named
    /// 'Theme/TextPrimary' found", iOS 18.5), so a named color comes out empty and the text is invisible.
    /// `ThemeSnippetColorsTests` checks these numbers against the catalog, so the two cannot drift apart.
    enum SnippetColors {
        /// Asset name (in `Assets.xcassets/Theme`) to its RGB value.
        static let rgb: [String: UInt32] = [
            "Background": 0xF3F5F1,
            "Surface": 0xFFFFFF,
            "MeaningBlock": 0xE8EEE9,
            "Hairline": 0xD9E0DA,
            "ChipFill": 0xDCEBE4,
            "TextPrimary": 0x1F2A26,
            "TextSecondary": 0x4A5A53,
            "TextMuted": 0x5F6E67,
            "Accent": 0x2F6F62,
        ]

        static let background = color("Background")
        static let surface = color("Surface")
        static let meaningBlock = color("MeaningBlock")
        static let hairline = color("Hairline")
        static let chipFill = color("ChipFill")
        static let textPrimary = color("TextPrimary")
        static let textSecondary = color("TextSecondary")
        static let textMuted = color("TextMuted")
        static let accent = color("Accent")

        private static func color(_ name: String) -> Color {
            let value = rgb[name] ?? 0
            return Color(
                red: Double((value >> 16) & 0xFF) / 255,
                green: Double((value >> 8) & 0xFF) / 255,
                blue: Double(value & 0xFF) / 255)
        }
    }
}
