//
//  Theme.swift
//  EnglishTranslation
//

import SwiftUI

/// The design tokens: every color, spacing and corner radius the app uses.
/// Rules and HIG links: `docs/design/design-system.md`.
/// Text has no tokens here on purpose: use Apple text styles (`.body`, `.headline`, ...).
enum Theme {

    /// Palette "Sage and Teal", light mode only. Values live in `Assets.xcassets/Theme`.
    enum Colors {
        /// Screen background, behind cards.
        static let background = Color("Theme/Background")
        /// Cards and round buttons.
        static let surface = Color("Theme/Surface")
        /// The tinted box that holds the simple English meaning.
        static let meaningBlock = Color("Theme/MeaningBlock")
        /// Thin borders and dividers.
        static let hairline = Color("Theme/Hairline")
        /// Background of small pill chips, such as the level chip.
        static let chipFill = Color("Theme/ChipFill")
        /// Main text.
        static let textPrimary = Color("Theme/TextPrimary")
        /// IPA, labels, icons.
        static let textSecondary = Color("Theme/TextSecondary")
        /// Placeholders and counts. Still passes 4.5:1 on the background.
        static let textMuted = Color("Theme/TextMuted")
        /// Used in one small place per screen: send button, level chip text, focus.
        static let accent = Color("Theme/Accent")
        /// Correct answer in the speaking score.
        static let success = Color("Theme/Success")
        /// Wrong word or failed request. Never the only signal: add an icon or underline.
        static let error = Color("Theme/Error")
        /// Example label "easy". Text color; the label background is this color at 14% opacity.
        static let levelEasy = Color("Theme/LevelEasy")
        /// Example label "medium" (amber). Same use as `levelEasy`.
        static let levelMedium = Color("Theme/LevelMedium")
        /// Example label "harder" (coral). Same use as `levelEasy`.
        static let levelHarder = Color("Theme/LevelHarder")
    }

    /// Spacing on a 4 pt grid.
    enum Spacing {
        static let xSmall: CGFloat = 4
        static let small: CGFloat = 8
        static let medium: CGFloat = 12
        static let large: CGFloat = 16
        static let xLarge: CGFloat = 24
    }

    /// Corner radius. Chips use `Capsule()` instead.
    enum Radius {
        /// Meaning block and small boxes inside a card.
        static let block: CGFloat = 12
        /// Cards and the composer.
        static let card: CGFloat = 20
    }

    /// Apple's minimum tap target (HIG, Accessibility).
    static let minTapTarget: CGFloat = 44
}
