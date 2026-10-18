//
//  WordHeaderView.swift
//  EnglishTranslation
//

import SwiftUI

/// The top of a word card: the headword, large, with the IPA after it on the same line (it goes under the headword
/// when the line is too long, as for "beat around the bush"). A form like "ran" shows the form pill under it instead.
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
                if let pill = entry.formPillText {
                    headword
                    FormPill(text: pill)
                } else if let ipa = entry.ipa {
                    // The developer's choice (TASK-83.10, mockup A): the IPA on the headword's line when it fits.
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.small) {
                            headword
                            ipaText(ipa)
                        }
                        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                            headword
                            ipaText(ipa)
                        }
                    }
                } else {
                    headword
                }
            }
            Spacer()
            HStack(spacing: Theme.Spacing.small) {
                actions()
            }
        }
    }

    private var headword: some View {
        Text(entry.matchedForm?.text ?? entry.word)
            .font(.largeTitle.weight(.semibold))
            .foregroundStyle(Theme.Colors.textPrimary)
            .speechHighlight(isPlaying: isSpeaking)
    }

    private func ipaText(_ ipa: String) -> some View {
        Text(ipa)
            .font(.callout)
            .foregroundStyle(Theme.Colors.textSecondary)
            .fixedSize()
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

#Preview("Phrase, IPA under a long headword") {
    WordHeaderView(entry: WordEntry(
        word: "beat around the bush", ipa: "/ˈbit əˈɹaʊnd ðə ˈbʊʃ/",
        senses: [Sense(pos: "idiom", cefr: "B2", simpleDefinition: "to avoid saying something directly",
                       vietnamese: "nói vòng vo", examples: [])])) {
        IconButton(systemImage: "speaker.wave.2", label: "Listen") {}
    }
    .padding()
    .background(Theme.Colors.background)
}
