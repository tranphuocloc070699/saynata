//
//  SentenceWordCard.swift
//  EnglishTranslation
//

import SwiftUI

/// A small card for one hard word in a sentence: the word, a form pill and a level chip,
/// then its short explanation. A single word can be tapped to open its full word card.
struct SentenceWordCard: View {
    let info: SentenceWordInfo
    /// Called when a single word is tapped. Not called for a phrase.
    var onOpen: () -> Void = {}

    var body: some View {
        if info.canOpenWordCard {
            Button(action: onOpen) { card }
                .buttonStyle(.plain)
                .accessibilityHint("Opens the full word card")
        } else {
            card
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            HStack(alignment: .center, spacing: Theme.Spacing.small) {
                Text(info.word)
                    .font(.body.weight(.medium))
                    .foregroundStyle(Theme.Colors.textPrimary)
                if let pill = info.pillText {
                    FormPill(text: pill)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                if let chip = info.chipText {
                    LevelChip(text: chip)
                        .fixedSize()
                }
            }
            Text(info.explanation)
                .font(.system(.body, design: .serif))
                .foregroundStyle(Theme.Colors.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .multilineTextAlignment(.leading)
        }
        .cardSurface()
        .contentShape(RoundedRectangle(cornerRadius: Theme.Radius.card))
    }
}

#Preview("From the dictionary, from the backend note, a phrase") {
    VStack(spacing: Theme.Spacing.medium) {
        SentenceWordCard(info: SentenceWordInfo(
            word: .init(word: "blurred", partOfSpeech: "verb", meaning: "made something hard to see clearly"),
            entry: WordEntry(
                word: "blur", ipa: "/blɝ/", senses: WordEntry.depend.senses,
                matchedForm: MatchedForm(text: "blurred", type: "past tense"))))
        SentenceWordCard(info: SentenceWordInfo(
            word: .init(word: "edges", partOfSpeech: "noun", meaning: "the outside parts (base form: edge)"),
            entry: nil))
        SentenceWordCard(info: SentenceWordInfo(
            word: .init(word: "drew out", partOfSpeech: "phrasal verb", meaning: "took out (base form: draw out)"),
            entry: nil))
    }
    .padding()
    .background(Theme.Colors.background)
}
