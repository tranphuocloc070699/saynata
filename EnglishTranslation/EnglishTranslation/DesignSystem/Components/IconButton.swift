//
//  IconButton.swift
//  EnglishTranslation
//

import SwiftUI

/// A round, icon-only button (CLAUDE.md "UI rule"). The tap target is always 44 pt, Apple's minimum.
/// `label` is read by VoiceOver, since there is no visible text.
struct IconButton: View {
    /// `regular` draws a 44 pt circle. `compact` draws a 32 pt circle inside the same 44 pt tap target,
    /// for rows of text, such as the speaker on a sense title or an example.
    enum Size {
        case regular, compact

        var circle: CGFloat { self == .compact ? 32 : Theme.minTapTarget }
    }

    let systemImage: String
    let label: String
    /// `true` only for the one main action on a screen, such as send.
    var isProminent = false
    var size: Size = .regular
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font((size == .compact ? Font.footnote : Font.body).weight(isProminent ? .semibold : .regular))
                .foregroundStyle(isProminent ? Color.white : Theme.Colors.textSecondary)
                .frame(width: size.circle, height: size.circle)
                .background(isProminent ? Theme.Colors.accent : Theme.Colors.surface, in: Circle())
                .overlay {
                    if !isProminent {
                        Circle().strokeBorder(Theme.Colors.hairline, lineWidth: 1)
                    }
                }
                .shadow(color: .black.opacity(isProminent ? 0 : 0.05), radius: 4, y: 1)
                .frame(width: Theme.minTapTarget, height: Theme.minTapTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

#Preview {
    HStack(spacing: Theme.Spacing.medium) {
        IconButton(systemImage: "doc.on.doc", label: "Copy") {}
        IconButton(systemImage: "speaker.wave.2", label: "Listen") {}
        IconButton(systemImage: "mic", label: "Speak") {}
        IconButton(systemImage: "arrow.up", label: "Send", isProminent: true) {}
        IconButton(systemImage: "speaker.wave.2", label: "Listen", size: .compact) {}
    }
    .padding()
    .background(Theme.Colors.background)
}
