//
//  FoldedRow.swift
//  EnglishTranslation
//

import SwiftUI

/// A row that opens and closes, such as "More examples", "How to use it" or "Vietnamese".
/// The parent owns `isExpanded`, so it can keep only one row open at a time.
/// The title is a section heading, not a button label, so it keeps its text (CLAUDE.md "UI rule").
struct FoldedRow<Content: View>: View {
    let title: String
    /// Shown after the title, for example "2". Optional.
    var count: Int?
    @Binding var isExpanded: Bool
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(.snappy) { isExpanded.toggle() }
            } label: {
                HStack(spacing: Theme.Spacing.small) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.Colors.textPrimary)
                    if let count {
                        Text("\(count)")
                            .font(.subheadline)
                            .foregroundStyle(Theme.Colors.textMuted)
                    }
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Theme.Colors.textSecondary)
                        .rotationEffect(.degrees(isExpanded ? 180 : 0))
                }
                .frame(minHeight: Theme.minTapTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(isExpanded ? "Open" : "Closed")

            if isExpanded {
                content
                    .padding(.bottom, Theme.Spacing.medium)
            }

            HairlineDivider()
        }
    }
}

#Preview {
    @Previewable @State var isExpanded = true

    FoldedRow(title: "Vietnamese", isExpanded: $isExpanded) {
        Text("Kỹ lưỡng, cẩn thận.")
    }
    .padding()
}
