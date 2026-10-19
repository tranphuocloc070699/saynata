//
//  SentenceCard.swift
//  EnglishTranslation
//

import SwiftUI

/// The sentence result (TASK-89.2, mockup Option B): one card with
/// - the sentence the user typed (serif, larger) and its Listen button;
/// - a hairline;
/// - the simple summary (serif) with its own Listen button, and the small caption "In simple words";
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

    private let sentenceSpeechID = "sentence"
    private let summarySpeechID = "summary"

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
                        LikeButton(item: SavedItem(kind: .sentence, text: sentence, preview: summary))
                    }
                }
            }

            HairlineDivider()

            summaryBlock

            // No translation yet (the answer is still streaming): the row appears when the full result arrives.
            if let translation {
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
                listenButton(text: summary, id: summarySpeechID, label: "Listen to the simple version")
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
