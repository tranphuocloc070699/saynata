# Saynata design system

The design system lives in code. This page holds the rules. Read it before any UI work.

- Tokens: [Theme.swift](../../EnglishTranslation/EnglishTranslation/DesignSystem/Theme.swift)
- Color values: `EnglishTranslation/EnglishTranslation/Assets.xcassets/Theme/` (one Color Set per role)
- Components: [DesignSystem/Components/](../../EnglishTranslation/EnglishTranslation/DesignSystem/Components/)
- See everything at once: open [DesignSystemGallery.swift](../../EnglishTranslation/EnglishTranslation/DesignSystem/DesignSystemGallery.swift) and its Xcode preview

![Gallery](design-system-gallery.png)

## Where the truth is

Code is the source of truth. To change a color, change the Color Set first, then this page.
There are no mockups in Figma or Claude Design. Build screens straight in SwiftUI with `#Preview`.

## Rules

1. **Follow Apple first.** When a question is not answered here, do what the
   [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) say.
   Do not invent a new style.
2. **Colors only from `Theme.Colors`.** No hex values or system colors (`.blue`, `.gray`) in views.
   Palette "Sage and Teal" (Option A in [color-schemes-2026-10-11.md](color-schemes-2026-10-11.md)).
3. **Accent in one small place per screen**: the send button, the level chip text, focus.
   Never for large areas.
4. **Light mode only.** `UIUserInterfaceStyle = Light` in `Info.plist`. To add dark mode later, add a
   "Dark" value to each Color Set and remove that key. The dark values are already in the color report.
5. **Text uses Apple text styles only**, never a fixed point size, so Dynamic Type works
   ([HIG: Typography](https://developer.apple.com/design/human-interface-guidelines/typography)).

   | Use | Style |
   |---|---|
   | Headword | `.largeTitle` semibold |
   | Section title | `.headline` |
   | Meaning (the hero) | `.body` serif (New York, 17 pt) |
   | Example sentence | `.callout` serif italic |
   | Folded row title | `.subheadline` semibold |
   | Chip | `.footnote` medium |
   | Small label ("MEANING") | `.caption` semibold, uppercase |

6. **Buttons are icon-only and 44 pt** (`IconButton`). 44×44 pt is Apple's minimum tap target
   ([HIG: Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)).
   Every icon button has an accessibility label. Icons are SF Symbols
   ([HIG: SF Symbols](https://developer.apple.com/design/human-interface-guidelines/sf-symbols)).
7. **Spacing and radius only from `Theme.Spacing` and `Theme.Radius`.** Spacing is on a 4 pt grid.
8. **Meaning first, translation last** (CLAUDE.md "Product rule"). The `MeaningBlock` is the hero.
   Vietnamese is a `FoldedRow` at the bottom, closed by default. One folded row open at a time.
9. **Show one example, fold the rest** (progressive disclosure: show what most people need, hide the rest
   behind one tap).
10. **Never use color alone to mean something.** A wrong word in the speaking score is red *and*
    underlined or marked with an icon ([HIG: Color](https://developer.apple.com/design/human-interface-guidelines/color)).

## Components

| Component | Job |
|---|---|
| `IconButton` | Round 44 pt button: white with a hairline border, or accent fill (`isProminent`) for the one main action |
| `LevelChip` | Pill with word type and CEFR level, for example "adj · B2" |
| `MeaningBlock` | Tinted box with the "Meaning" label, serif meaning, and one optional example |
| `FoldedRow` | Row that opens and closes, with an optional count; the parent decides which row is open |
| `HairlineDivider` | One-pixel line in the hairline color. Use it, not `Divider().overlay(...)`, which flickers while scrolling |

Add a new component only when two screens need the same thing. Add it to the gallery in the same change.
