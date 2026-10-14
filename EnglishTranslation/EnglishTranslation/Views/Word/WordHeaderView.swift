//
//  WordHeaderView.swift
//  EnglishTranslation
//

import SwiftUI

/// The top of a word card: the headword, large, with the IPA (or the form pill) under it.
/// The buttons on the right come from the caller: the word card passes Listen, the share sheet passes three.
struct WordHeaderView<Actions: View>: View {
    let entry: WordEntry
    /// True while the word is read aloud (F3). The word then gets a soft highlight.
    var isSpeaking = false
    @ViewBuilder var actions: () -> Actions

    /// The word as typed (a form like "ran" stays "ran"). With a form, the pill shows the base word.
    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.small) {
            VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                Text(entry.matchedForm?.text ?? entry.word)
                    .font(.largeTitle.weight(.semibold))
                    .foregroundStyle(Theme.Colors.textPrimary)
                    .speechHighlight(isPlaying: isSpeaking)
                if let pill = entry.formPillText {
                    FormPill(text: pill)
                } else if let ipa = entry.ipa {
                    Text(ipa)
                        .font(.callout)
                        .foregroundStyle(Theme.Colors.textSecondary)
                }
            }
            Spacer()
            HStack(spacing: Theme.Spacing.small) {
                actions()
            }
        }
    }
}

#Preview("Header with three buttons") {
    WordHeaderView(entry: .depend) {
        IconButton(systemImage: "speaker.wave.2", label: "Listen") {}
        IconButton(systemImage: "arrow.up.forward.app", label: "Open in Saynata") {}
        IconButton(systemImage: "xmark", label: "Close") {}
    }
    .padding()
    .background(Theme.Colors.background)
}
