//
//  IconButton.swift
//  EnglishTranslation
//

import SwiftUI

/// A round, icon-only button (CLAUDE.md "UI rule"). Always 44 pt, Apple's minimum tap target.
/// `label` is read by VoiceOver, since there is no visible text.
struct IconButton: View {
    let systemImage: String
    let label: String
    /// `true` only for the one main action on a screen, such as send.
    var isProminent = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.body.weight(isProminent ? .semibold : .regular))
                .foregroundStyle(isProminent ? Color.white : Theme.Colors.textSecondary)
                .frame(width: Theme.minTapTarget, height: Theme.minTapTarget)
                .background(isProminent ? Theme.Colors.accent : Theme.Colors.surface, in: Circle())
                .overlay {
                    if !isProminent {
                        Circle().strokeBorder(Theme.Colors.hairline, lineWidth: 1)
                    }
                }
                .shadow(color: .black.opacity(isProminent ? 0 : 0.05), radius: 4, y: 1)
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
    }
    .padding()
    .background(Theme.Colors.background)
}
