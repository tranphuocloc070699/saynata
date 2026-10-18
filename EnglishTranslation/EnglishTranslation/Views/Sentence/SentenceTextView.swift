//
//  SentenceTextView.swift
//  EnglishTranslation
//

import SwiftUI

/// One sentence in the result card (TASK-89.2): serif text where
/// - a tap on a word highlights it and opens the word sheet (bundled dictionary first, then the backend);
/// - a selected part opens a popup under the selection that explains the part (iPhone and iPad).
/// The original and the summary both use it, and so does the text inside the popup.
struct SentenceTextView: View {
    let text: String
    var style: SentenceTextStyle = .summary
    let language: TargetLanguage
    /// Gets the full word card for a tapped word. The real one is `TranslationStore.wordEntry(for:)`.
    var loadWordEntry: (SentenceWordInfo) async throws -> WordEntry = { word in
        guard let entry = word.entry else { throw WordLookupError.notOneWord }
        return entry
    }
    /// False for the text inside the popup: its words can be tapped, but a new selection does not open another popup.
    var allowsPartPopup = true

    @State private var openedWord: SentenceWordInfo?
    @State private var highlightedRange: NSRange?
    @State private var popupSelection: SentenceSelection?
    @State private var clearSelectionCount = 0

    var body: some View {
        SelectableSentenceText(
            text: text,
            style: style,
            highlightedRange: highlightedRange,
            allowsSelection: allowsPartPopup,
            clearSelectionCount: clearSelectionCount,
            onWordTap: open(_:),
            onSelectionSettled: handle(_:))
            .frame(maxWidth: .infinity, alignment: .leading)
            .sheet(item: $openedWord, onDismiss: { highlightedRange = nil }) { info in
                SentenceWordSheet(info: info, targetLanguage: language, load: loadWordEntry)
                    .presentationDetents([.medium, .large])
            }
            #if os(iOS)
            .popover(
                item: $popupSelection,
                attachmentAnchor: .rect(.rect(popupSelection?.rect ?? .zero)),
                arrowEdge: .top
            ) { selection in
                if case let .phrase(part) = selection.part {
                    SentencePartPopup(part: part, language: language, loadWordEntry: loadWordEntry)
                        .presentationCompactAdaptation(.popover)
                }
            }
            // The popup closed (a tap outside): the text should not stay selected.
            .onChange(of: popupSelection) { _, selection in
                if selection == nil { clearSelectionCount += 1 }
            }
            #endif
    }

    /// A tap on a word.
    private func open(_ token: SentenceToken) {
        highlightedRange = token.range
        openedWord = SentenceWordInfo(tappedWord: token.lookupWord)
    }

    /// The selection stopped changing. One word opens the word sheet, more opens the popup.
    private func handle(_ selection: SentenceSelection) {
        switch selection.part {
        case let .word(token):
            clearSelectionCount += 1
            open(token)
        case .phrase:
            popupSelection = selection
        }
    }
}

#Preview {
    SentenceTextView(
        text: "The mist blurred the edges of the world, so the hikers turned back before noon.",
        style: .original,
        language: .vietnamese,
        loadWordEntry: { _ in .depend })
        .padding()
        .background(Theme.Colors.background)
        .environment(SpeechPlayer.silent())
}
