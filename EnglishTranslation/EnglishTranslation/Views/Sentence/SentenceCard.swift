//
//  SentenceCard.swift
//  EnglishTranslation
//

import SwiftUI

/// The sentence result (TASK-89.2, mockup Option B): one card with
/// - the sentence the user typed (serif, larger) and its Listen button;
/// - a hairline;
/// - the simple summary (serif) with its own Listen button, and the small caption "In simple words";
/// - when the text has a real mistake, the folded row "Better way to say it" (closed): the fixed text with the
///   changed words in bold, its Listen and Copy buttons, and one line that says why (TASK-110);
/// - the target language, folded and closed (CLAUDE.md "Product rule"), last.
/// Under the card, on the right, one Copy button for the original sentence.
///
/// Used by the finished result and by the loading screens, so nothing jumps when the full result arrives:
/// the original shows at once, the summary grows word by word, and the target-language row appears at the end.
struct SentenceCard: View {
    let sentence: String
    /// The simple summary so far. Empty while nothing is written: the card shows a spinner there.
    let summary: String
    /// Nil while the answer is still streaming: the folded row is left out until it is there.
    let translation: String?
    /// The language the translation was made in. It titles the folded row.
    let language: TargetLanguage
    /// The text with its mistakes fixed. Nil hides the row: no fix, or text from another app.
    var better: String?
    /// One line that says why the fix is better.
    var betterWhy: String?
    /// Gets the full word card for a tapped word. The real one is `TranslationStore.wordEntry(for:)`.
    var loadWordEntry: (SentenceWordInfo) async throws -> WordEntry = { word in
        guard let entry = word.entry else { throw WordLookupError.notOneWord }
        return entry
    }

    @Environment(SpeechPlayer.self) private var speechPlayer
    /// Starts the way "Show translation" in Settings says.
    @State private var isTranslationOpen = TranslationRowPreference().startsOpen
    /// True for a moment after Copy, so the icon shows a check mark.
    @State private var didCopy = false
    /// "Better way to say it" starts closed, like the language row.
    @State private var isBetterOpen = false
    @State private var didCopyBetter = false

    private let sentenceSpeechID = "sentence"
    private let summarySpeechID = "summary"
    private let betterSpeechID = "better"

    /// True when the backend sent a real language line. It sends "" (never null) when it has none.
    static func hasLanguageLine(_ translation: String) -> Bool {
        !translation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        VStack(alignment: .trailing, spacing: Theme.Spacing.small) {
            card
            IconButton(systemImage: didCopy ? "checkmark" : "doc.on.doc", label: "Copy the sentence", action: copy)
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
            HStack(alignment: .top, spacing: Theme.Spacing.small) {
                SentenceTextView(text: sentence, style: .original, language: language, loadWordEntry: loadWordEntry)
                    .speechHighlight(isPlaying: speechPlayer.currentID == sentenceSpeechID)
                    .padding(.top, Theme.Spacing.small)
                HStack(spacing: Theme.Spacing.small) {
                    listenButton(text: sentence, id: sentenceSpeechID, label: "Listen to the sentence")
                    // Only for a finished answer: the summary is what the Liked list shows.
                    if translation != nil {
                        LikeButton(item: SavedItem(kind: .sentence, text: sentence, preview: SummaryMarkup(summary).plainText))
                    }
                }
            }

            HairlineDivider()

            summaryBlock

            // Only with the finished answer, so the row does not appear in the middle of the stream.
            if translation != nil, let better {
                betterRow(better)
            }

            // No translation yet (the answer is still streaming): the row appears when the full result arrives.
            // "" means the backend had no line for this language (a guest, offline cache): no row (TASK-106.6).
            if let translation, Self.hasLanguageLine(translation) {
                FoldedRow(title: language.title, isExpanded: $isTranslationOpen) {
                    Text(translation)
                        .font(.body)
                        .foregroundStyle(Theme.Colors.textPrimary)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .onChange(of: isTranslationOpen) { _, isOpen in
                    TranslationRowPreference().rowChanged(isOpen: isOpen)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardSurface()
    }

    @ViewBuilder
    private var summaryBlock: some View {
        if summary.isEmpty {
            // Nothing written yet (TASK-74): a spinner where the summary will appear.
            ProgressView()
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            HStack(alignment: .top, spacing: Theme.Spacing.small) {
                VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                    // The summary grows word by word while it streams (TASK-73): the text view measures
                    // its height again each time, so the line count follows the text.
                    SentenceTextView(text: summary, style: .summary, language: language, loadWordEntry: loadWordEntry)
                        .speechHighlight(isPlaying: speechPlayer.currentID == summarySpeechID)
                    Text("In simple words")
                        .font(.caption)
                        .foregroundStyle(Theme.Colors.textMuted)
                }
                .padding(.top, Theme.Spacing.small)
                listenButton(text: SummaryMarkup(summary).plainText, id: summarySpeechID, label: "Listen to the simple version")
            }
        }
    }

    private func betterRow(_ better: String) -> some View {
        FoldedRow(title: "Better way to say it", isExpanded: $isBetterOpen) {
            VStack(alignment: .leading, spacing: Theme.Spacing.xSmall) {
                HStack(alignment: .top, spacing: Theme.Spacing.small) {
                    Text(BetterDiff(original: sentence, better: better).attributedString)
                        .font(.system(.body, design: .serif))
                        .foregroundStyle(Theme.Colors.textPrimary)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .speechHighlight(isPlaying: speechPlayer.currentID == betterSpeechID)
                        .padding(.top, Theme.Spacing.small)
                    listenButton(text: better, id: betterSpeechID, label: "Listen to the better version")
                    IconButton(
                        systemImage: didCopyBetter ? "checkmark" : "doc.on.doc",
                        label: "Copy the better version"
                    ) {
                        copyBetter(better)
                    }
                }
                if let betterWhy {
                    Text(betterWhy)
                        .font(.subheadline)
                        .foregroundStyle(Theme.Colors.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func listenButton(text: String, id: String, label: String) -> some View {
        IconButton(
            systemImage: speechPlayer.currentID == id ? "stop.fill" : "speaker.wave.2",
            label: speechPlayer.currentID == id ? "Stop" : label
        ) {
            if speechPlayer.currentID == id {
                speechPlayer.stop()
            } else {
                speechPlayer.speak(text, id: id)
            }
        }
    }

    private func copyBetter(_ better: String) {
        Pasteboard.copy(better)
        didCopyBetter = true
        Task {
            try? await Task.sleep(for: .seconds(1.5))
            didCopyBetter = false
        }
    }

    private func copy() {
        Pasteboard.copy(sentence)
        didCopy = true
        Task {
            try? await Task.sleep(for: .seconds(1.5))
            didCopy = false
        }
    }
}

#Preview {
    SentenceCard(
        sentence: "The mist blurred the edges of the world, so the hikers turned back before noon.",
        summary: "The fog made everything hard to see, so the walkers went back before midday.",
        translation: "Sương mù làm mờ mọi thứ, nên những người đi bộ quay lại trước buổi trưa.",
        language: .vietnamese,
        loadWordEntry: { _ in .depend })
        .padding()
        .background(Theme.Colors.background)
        .environment(SpeechPlayer.silent())
}

#Preview("Better way to say it") {
    SentenceCard(
        sentence: "Please open the light, it's too dark.",
        summary: "You want someone to make the room bright.",
        translation: "Làm ơn bật đèn lên, tối quá.",
        language: .vietnamese,
        better: "Please turn on the light. It's too dark.",
        betterWhy: "In English you \"turn on\" a light, not \"open\" it.",
        loadWordEntry: { _ in .depend })
        .padding()
        .background(Theme.Colors.background)
        .environment(SpeechPlayer.silent())
}

#Preview("Long text: gist and bullets") {
    SentenceCard(
        sentence: "But the dev account getting banned would impact your career correct? Then in my mind, setting up "
            + "private distribution, speeding up rolling out updates and making things more efficient can only benefit you.",
        summary: "Your **career** is at risk if the developer account is banned.\n"
            + "- Use **private distribution** and faster updates to protect yourself.\n"
            + "- If the company keeps hiding **bad actions**, leave.",
        translation: "Nếu tài khoản bị cấm, sự nghiệp của bạn sẽ bị ảnh hưởng.",
        language: .vietnamese,
        loadWordEntry: { _ in .depend })
        .padding()
        .background(Theme.Colors.background)
        .environment(SpeechPlayer.silent())
}

#Preview("Waiting for the summary") {
    SentenceCard(
        sentence: "The mist blurred the edges of the world.",
        summary: "",
        translation: nil,
        language: .vietnamese)
        .padding()
        .background(Theme.Colors.background)
        .environment(SpeechPlayer.silent())
}
