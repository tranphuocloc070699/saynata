//
//  DesignSystemGallery.swift
//  EnglishTranslation
//

import SwiftUI

/// Every color, text style and component on one screen. Open the preview in Xcode to
/// check the design system. Not shown in the app.
struct DesignSystemGallery: View {
    /// All rows start closed: Vietnamese is collapsed by default (CLAUDE.md "Product rule").
    @State private var openRow = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.xLarge) {
                colorsSection
                textStylesSection
                componentsSection
                wordCardSample
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
            Spacer()
            IconButton(systemImage: "star", label: "Save") {}
        }
    }

    // MARK: - Word card sample

    /// The parts put together, in the order from CLAUDE.md: meaning first, Vietnamese last.
    private var wordCardSample: some View {
        TitledSection(title: "Word card") {
            VStack(alignment: .leading, spacing: Theme.Spacing.large) {
                HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.small) {
                    Text("thorough").font(.largeTitle.weight(.semibold))
                    Text("/ˈθʌr.ə/")
                        .font(.callout)
                        .foregroundStyle(Theme.Colors.textSecondary)
                    LevelChip(text: "adj · B2")
                }
                .foregroundStyle(Theme.Colors.textPrimary)

                iconRow

                MeaningBlock(
                    meaning: "Very careful to do things well and correctly.",
                    example: "She is thorough in her work."
                )

                VStack(spacing: 0) {
                    foldedRow("More examples", count: 1, text: "The police did a thorough search of the house.")
                    foldedRow("How to use it", count: 2, text: "be thorough in something")
                    foldedRow("Vietnamese", text: "Kỹ lưỡng, cẩn thận.")
                }
            }
            .padding(Theme.Spacing.large)
            .background(Theme.Colors.surface, in: RoundedRectangle(cornerRadius: Theme.Radius.card))
        }
    }

    /// One folded row. Opening one closes the others.
    private func foldedRow(_ title: String, count: Int? = nil, text: String) -> some View {
        FoldedRow(
            title: title,
            count: count,
            isExpanded: Binding(
                get: { openRow == title },
                set: { openRow = $0 ? title : "" }
            )
        ) {
            Text(text)
                .font(.body)
                .foregroundStyle(Theme.Colors.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

#Preview {
    DesignSystemGallery()
}
