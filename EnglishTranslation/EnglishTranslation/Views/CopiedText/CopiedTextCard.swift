//
//  CopiedTextCard.swift
//  EnglishTranslation
//

import SwiftUI

/// The Shortcuts card for "Explain copied text" (TASK-87, layout B). It shows over the current app after a
/// Back Tap or the Action Button. The simple English meaning comes first, up to three meanings or words,
/// and the target-language line is the last, small line (CLAUDE.md "Product rule").
///
/// A Shortcuts card on iOS 18 cannot run buttons, so it has no folding rows and no speaker. The only control is a
/// link that opens the full result in Saynata (icon only).
struct CopiedTextCard: View {
    let result: CopiedTextResult
    var language: TargetLanguage = .vietnamese

    /// A card is short: more than this many meanings or words is the app's job.
    static let maxItems = 3

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
            content
        }
        .padding(Theme.Spacing.large)
        .frame(maxWidth: .infinity, alignment: .leading)
        // The Open link sits over the top corner; `header` leaves room for it next to the title.
        .overlay(alignment: .topTrailing) {
            if let text = result.text, let url = DeepLink.lookupURL(text: text) {
                OpenInSaynataLink(url: url, isSignIn: isSignIn)
                    .padding(Theme.Spacing.small)
            }
        }
        .background(Colors.background)
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        switch result {
        case let .word(entry):
            header { WordTitle(entry: entry) }
            ForEach(Array(entry.senses.prefix(Self.maxItems).enumerated()), id: \.offset) { _, sense in
                VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                    Chip(text: sense.cefr.isEmpty ? sense.pos : "\(sense.pos) · \(sense.cefr)")
                    Text(sense.simpleDefinition)
                        .font(.body)
                        .foregroundStyle(Colors.textPrimary)
                }
            }
            targetLanguageLine(
                "Vietnamese", text: Self.uniqueJoined(entry.senses.prefix(Self.maxItems).map(\.vietnamese)))

        case let .sentence(sentence, explanation):
            header { QuotedSentence(text: sentence) }
            Meaning(text: explanation.meaning)
            wordList(explanation.words.prefix(Self.maxItems).map { ($0.word, $0.partOfSpeech, $0.meaning) })
            targetLanguageLine(language.title, text: explanation.translation)

        case let .signInNeeded(text, localWords):
            header { MessageTitle(text: "Sign in to explain new text") }
            message("\u{201C}\(text)\u{201D} is not saved yet. Open Saynata and sign in to explain it.")
            localWordList(localWords)

        case let .slow(text, localWords):
            header { MessageTitle(text: "Still working\u{2026}") }
            message("The network is slow. Open Saynata to see the answer for \u{201C}\(text)\u{201D}.")
            localWordList(localWords)

        case let .failed(_, text):
            header { MessageTitle(text: "Couldn't explain this") }
            message(text)

        case let .problem(title, text):
            header { MessageTitle(text: title) }
            message(text)
        }
    }

    /// The title, with room on the right for the Open link that sits over the card's top corner.
    private func header(@ViewBuilder title: () -> some View) -> some View {
        title()
            .padding(.trailing, result.text == nil ? 0 : Theme.minTapTarget)
    }

    private var isSignIn: Bool {
        if case .signInNeeded = result { return true }
        return false
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(Colors.textSecondary)
    }

    /// Up to three words, one per line: the word, its type, and a short meaning.
    @ViewBuilder
    private func wordList(_ words: [(word: String, type: String, meaning: String)]) -> some View {
        if !words.isEmpty {
            VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                ForEach(words, id: \.word) { item in
                    (Text(item.word).fontWeight(.semibold).foregroundColor(Colors.textPrimary)
                        + Text(item.type.isEmpty ? "  " : "  \(item.type)  ").foregroundColor(Colors.textMuted)
                        + Text(item.meaning).foregroundColor(Colors.textSecondary))
                        .font(.subheadline)
                }
            }
        }
    }

    /// The sentence's bundled dictionary words, while the full answer is not here.
    private func localWordList(_ words: [SentenceWordInfo]) -> some View {
        wordList(words.prefix(Self.maxItems).map { ($0.word, $0.entry?.senses.first?.pos ?? "", $0.explanation) })
    }

    /// The last line, small: "Vietnamese · miễn cưỡng". Hidden when there is no text.
    @ViewBuilder
    private func targetLanguageLine(_ title: String, text: String) -> some View {
        if !text.isEmpty {
            VStack(alignment: .leading, spacing: Theme.Spacing.small) {
                Rectangle().fill(Colors.hairline).frame(height: 1)
                Text("\(title) · \(text)")
                    .font(.footnote)
                    .foregroundStyle(Colors.textSecondary)
            }
        }
    }

    /// "miễn cưỡng; không sẵn lòng", without repeats or empty parts.
    static func uniqueJoined(_ parts: [String]) -> String {
        var seen = Set<String>()
        return parts
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
            .joined(separator: "; ")
    }
}

// MARK: - Pieces

/// Not `Theme.Colors`: Shortcuts cannot read the asset catalog (see `Theme.SnippetColors`). For the same reason the
/// card has small copies of `LevelChip` and `MeaningBlock` below.
private typealias Colors = Theme.SnippetColors

/// Like `LevelChip`: "verb · A1".
private struct Chip: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.footnote.weight(.medium))
            .foregroundStyle(Colors.accent)
            .padding(.horizontal, Theme.Spacing.small)
            .padding(.vertical, Theme.Spacing.xSmall)
            .background(Colors.chipFill, in: Capsule())
    }
}

/// Like `MeaningBlock`, without its label: the simple English meaning in the tinted box.
private struct Meaning: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(.body, design: .serif))
            .foregroundStyle(Colors.textPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Theme.Spacing.medium)
            .background(Colors.meaningBlock, in: RoundedRectangle(cornerRadius: Theme.Radius.block))
    }
}

/// "reluctant /rɪˈlʌktənt/", or "ran, past tense of run".
private struct WordTitle: View {
    let entry: WordEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.small) {
                Text(entry.word)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(Colors.textPrimary)
                if let ipa = entry.ipa, !ipa.isEmpty {
                    Text(ipa)
                        .font(.subheadline)
                        .foregroundStyle(Colors.textSecondary)
                }
            }
            if let form = entry.matchedForm {
                Text("\u{201C}\(form.text)\u{201D} is the \(form.type)")
                    .font(.footnote)
                    .foregroundStyle(Colors.textMuted)
            }
        }
    }
}

/// The copied sentence in quotes, in serif, like the share sheet.
private struct QuotedSentence: View {
    let text: String

    var body: some View {
        Text("\u{201C}\(text)\u{201D}")
            .font(.system(.body, design: .serif))
            .foregroundStyle(Colors.textPrimary)
            .lineLimit(3)
    }
}

private struct MessageTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.headline)
            .foregroundStyle(Colors.textPrimary)
    }
}

/// The round "Open in Saynata" icon. A link, not a button: links work in a Shortcuts card, buttons do not.
private struct OpenInSaynataLink: View {
    let url: URL
    /// For a guest, the same link opens the app, which then shows its sign-in sheet.
    var isSignIn = false

    var body: some View {
        Link(destination: url) {
            Image(systemName: isSignIn ? "person.crop.circle" : "arrow.up.forward.app")
                .font(.footnote)
                .foregroundStyle(Colors.textSecondary)
                .frame(width: 32, height: 32)
                .background(Colors.surface, in: Circle())
                .overlay { Circle().strokeBorder(Colors.hairline, lineWidth: 1) }
                .frame(width: Theme.minTapTarget, height: Theme.minTapTarget)
        }
        .accessibilityLabel(isSignIn ? "Open Saynata to sign in" : "Open in Saynata")
    }
}

#Preview("Word") {
    CopiedTextCard(result: .word(.run))
}

#Preview("Sentence") {
    CopiedTextCard(result: .sentence(
        "She was reluctant to leave the party early.",
        Explanation(
            meaning: "She did not want to leave the party before it ended.",
            words: [
                .init(word: "reluctant", partOfSpeech: "adjective", meaning: "not wanting to do something"),
                .init(word: "early", partOfSpeech: "adverb", meaning: "before the usual time"),
            ],
            translation: "Cô ấy không muốn rời bữa tiệc sớm.")))
}

#Preview("Sign in") {
    CopiedTextCard(result: .signInNeeded("serendipity", localWords: []))
}

#Preview("Empty") {
    CopiedTextCard(result: .problem(title: "Nothing to explain", message: "Copy some English text, then try again."))
}
