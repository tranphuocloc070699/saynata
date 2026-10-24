//
//  SentencePartPopup.swift
//  EnglishTranslation
//

import SwiftUI

/// The popup under a selected part of a sentence (TASK-89.2, mockup Option B):
/// a small sparkle crumb with the part in quotes, its simple summary (it grows while it streams),
/// Listen and Copy icon buttons, and the target language folded and closed.
/// Words in the summary can be tapped like in the main card.
struct SentencePartPopup: View {
    let part: String
    let language: TargetLanguage
    var loadWordEntry: (SentenceWordInfo) async throws -> WordEntry = { word in
        guard let entry = word.entry else { throw WordLookupError.notOneWord }
        return entry
    }

    @Environment(\.makeSentencePartStore) private var makeStore
    @Environment(SpeechPlayer.self) private var speechPlayer
    @State private var store: SentencePartStore?
    /// Starts the way "Show translation" in Settings says.
    @State private var isTranslationOpen = TranslationRowPreference().startsOpen
    /// True for a moment after Copy, so the icon shows a check mark.
    @State private var didCopy = false

    private let speechID = "part"

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            crumb
            if let store {
                content(for: store.phase)
            } else {
                ProgressView().frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(Theme.Spacing.large)
        .frame(minWidth: 280, idealWidth: 330, maxWidth: 380, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .background(Theme.Colors.surface)
        .presentationBackground(Theme.Colors.surface)
        .task(id: part) {
            let newStore = makeStore()
            store = newStore
            await newStore.explain(part, language: language)
        }
        .onDisappear { speechPlayer.stop() }
    }

    /// The sparkle and the selected part in quotes.
    private var crumb: some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.xSmall) {
            Image(systemName: "sparkle")
                .font(.caption)
                .foregroundStyle(Theme.Colors.accent)
                .accessibilityHidden(true)
            Text("\u{201C}\(part)\u{201D}")
                .font(.caption)
                .foregroundStyle(Theme.Colors.textSecondary)
                .lineLimit(3)
        }
    }

    @ViewBuilder
    private func content(for phase: SentencePartStore.Phase) -> some View {
        switch phase {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, alignment: .leading)
        case let .failed(message):
            Text(message)
                .font(.subheadline)
                .foregroundStyle(Theme.Colors.error)
                .frame(maxWidth: .infinity, alignment: .leading)
        case .summary, .loaded:
            SentenceTextView(
                text: phase.summaryText,
                style: .summary,
                language: language,
                loadWordEntry: loadWordEntry,
                allowsPartPopup: false)
            buttonRow(summary: phase.summaryText)
            if case let .loaded(_, translation) = phase, SentenceCard.hasLanguageLine(translation) {
                FoldedRow(title: language.title, isExpanded: $isTranslationOpen) {
                    Text(translation)
                        .font(.body)
                        .foregroundStyle(Theme.Colors.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .onChange(of: isTranslationOpen) { _, isOpen in
                    TranslationRowPreference().rowChanged(isOpen: isOpen)
                }
            }
        }
    }

    /// "In simple words", then Listen (reads the summary) and Copy (copies the selected part).
    private func buttonRow(summary: String) -> some View {
        HStack(spacing: 0) {
            Text("In simple words")
                .font(.caption)
                .foregroundStyle(Theme.Colors.textMuted)
            Spacer(minLength: Theme.Spacing.small)
            IconButton(
                systemImage: speechPlayer.currentID == speechID ? "stop.fill" : "speaker.wave.2",
                label: speechPlayer.currentID == speechID ? "Stop" : "Listen to the simple version",
                size: .compact
            ) {
                if speechPlayer.currentID == speechID {
                    speechPlayer.stop()
                } else {
                    speechPlayer.speak(SummaryMarkup(summary).plainText, id: speechID)
                }
            }
            IconButton(
                systemImage: didCopy ? "checkmark" : "doc.on.doc",
                label: "Copy the selected part",
                size: .compact
            ) {
                Pasteboard.copy(part)
                didCopy = true
                Task {
                    try? await Task.sleep(for: .seconds(1.5))
                    didCopy = false
                }
            }
        }
    }
}

#Preview("Streaming, then loaded") {
    SentencePartPopup(part: "blurred the edges of the world", language: .vietnamese)
        .environment(\.makeSentencePartStore, { SentencePartStore(makeTranslator: { PreviewPartTranslator() }) })
        .environment(SpeechPlayer.silent())
        .padding()
        .background(Theme.Colors.background)
}

/// A translator for previews and the gallery: a short summary that streams, no network.
struct PreviewPartTranslator: Translating {
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        Explanation(
            meaning: "made the outside of everything hard to see",
            words: [],
            translation: "làm mờ đi phần rìa của mọi thứ")
    }
}
