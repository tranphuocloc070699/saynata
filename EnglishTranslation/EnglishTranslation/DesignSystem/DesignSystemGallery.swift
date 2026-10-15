//
//  DesignSystemGallery.swift
//  EnglishTranslation
//

import SwiftUI

/// Every color, text style and component on one screen. Open the preview in Xcode to
/// check the design system. Not shown in the app.
struct DesignSystemGallery: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.xLarge) {
                colorsSection
                textStylesSection
                componentsSection
                wordCardSample
                sentenceCardsSample
                codeBoxesSample
            }
            .padding(Theme.Spacing.large)
        }
        .background(Theme.Colors.background)
    }

    // MARK: - Colors

    private let swatches: [(name: String, color: Color)] = [
        ("background", Theme.Colors.background),
        ("surface", Theme.Colors.surface),
        ("meaningBlock", Theme.Colors.meaningBlock),
        ("hairline", Theme.Colors.hairline),
        ("chipFill", Theme.Colors.chipFill),
        ("textPrimary", Theme.Colors.textPrimary),
        ("textSecondary", Theme.Colors.textSecondary),
        ("textMuted", Theme.Colors.textMuted),
        ("accent", Theme.Colors.accent),
        ("success", Theme.Colors.success),
        ("error", Theme.Colors.error),
    ]

    private var colorsSection: some View {
        TitledSection(title: "Colors") {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 96))], spacing: Theme.Spacing.medium) {
                ForEach(swatches, id: \.name) { swatch in
                    VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                        RoundedRectangle(cornerRadius: Theme.Radius.block)
                            .fill(swatch.color)
                            .frame(height: 48)
                            .overlay {
                                RoundedRectangle(cornerRadius: Theme.Radius.block)
                                    .strokeBorder(Theme.Colors.hairline)
                            }
                        Text(swatch.name)
                            .font(.caption)
                            .foregroundStyle(Theme.Colors.textSecondary)
                    }
                }
            }
        }
    }

    // MARK: - Text styles

    private var textStylesSection: some View {
        TitledSection(title: "Text styles") {
            VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                Text("thorough").font(.largeTitle.weight(.semibold))
                Text("Headline: section titles").font(.headline)
                Text("Body serif: the meaning").font(.system(.body, design: .serif))
                Text("Body: normal text").font(.body)
                Text("Callout serif italic: an example").font(.system(.callout, design: .serif).italic())
                Text("Subheadline: folded row titles").font(.subheadline)
                Text("Footnote: chips").font(.footnote)
                Text("Caption: small labels").font(.caption)
            }
            .foregroundStyle(Theme.Colors.textPrimary)
        }
    }

    // MARK: - Components

    private var componentsSection: some View {
        TitledSection(title: "Components") {
            VStack(alignment: .leading, spacing: Theme.Spacing.large) {
                iconRow
                LevelChip(text: "adj · B2")
                MeaningBlock(meaning: "Very careful to do things well and correctly.")
            }
        }
    }

    private var iconRow: some View {
        HStack(spacing: Theme.Spacing.small) {
            IconButton(systemImage: "doc.on.doc", label: "Copy") {}
            IconButton(systemImage: "speaker.wave.2", label: "Listen") {}
            IconButton(systemImage: "mic", label: "Speak") {}
            IconButton(systemImage: "speaker.wave.2", label: "Listen", size: .compact) {}
            Spacer()
            IconButton(systemImage: "star", label: "Save") {}
        }
    }

    // MARK: - Word card

    /// The real word cards with sample data: full card, then the compact one.
    private var wordCardSample: some View {
        TitledSection(title: "Word card") {
            VStack(alignment: .leading, spacing: Theme.Spacing.large) {
                WordCardView(entry: .depend)
                WordCardCompactView(entry: .depend)
            }
        }
    }

    // MARK: - Code boxes

    @State private var sampleCode = "48"
    @FocusState private var isSampleCodeFocused: Bool

    /// The one-time code boxes of the sign-in sheet (TASK-70). Tap them and type.
    private var codeBoxesSample: some View {
        TitledSection(title: "Code boxes") {
            CodeBoxes(code: $sampleCode, isFocused: $isSampleCodeFocused)
        }
    }

    // MARK: - Sentence cards

    /// The sentence result cards (TASK-41): header, Meaning card, word cards, and the character counter.
    private var sentenceCardsSample: some View {
        TitledSection(title: "Sentence cards") {
            VStack(alignment: .leading, spacing: Theme.Spacing.large) {
                SentenceHeader(sentence: "The mist blurred the edges of the world.")
                MeaningCard(
                    meaning: "Thick mist made the shapes of things look unclear.",
                    translation: "Sương mù làm mờ đi các đường nét của thế giới.",
                    language: .vietnamese)
                SentenceWordCard(info: SentenceWordInfo(
                    word: .init(word: "edges", partOfSpeech: "noun", meaning: "the outer part (base form: edge)"),
                    entry: nil))
                CharacterCounter(count: 41, limit: 500)
                CharacterCounter(count: 512, limit: 500)
            }
        }
    }
}

#Preview {
    DesignSystemGallery()
        .environment(SpeechPlayer.silent())
}
