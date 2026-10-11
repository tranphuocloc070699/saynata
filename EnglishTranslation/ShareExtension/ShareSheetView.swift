//
//  ShareSheetView.swift
//  ShareExtension
//

import SwiftUI

/// The sheet shown over Safari, Messages and other apps when the user shares selected text to Saynata.
///
/// A header row (the headword with its IPA, or the quoted sentence) with three icon buttons on the right:
/// Listen, Open in Saynata, Close. Below it the app's own views, as they are: `WordCardView` for a word,
/// `TranslateResultView` for a sentence, so this sheet picks up their style when they change.
struct ShareSheetView: View {
    /// Reads the shared text from the host app. Called once, when the sheet appears.
    let loadText: () async -> String
    /// Opens the Saynata app on this link (`saynata://lookup?text=...`).
    let onOpenApp: (URL) -> Void
    let onClose: () -> Void

    @State private var store = TranslationStore()
    /// Nil while the text is still being read.
    @State private var sharedText: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.large) {
                content
            }
            .padding(Theme.Spacing.large)
        }
        .background(Theme.Colors.background)
        .task { await explainSharedText() }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        if let sharedText {
            let route = TextRoute(text: sharedText)
            if let message = route.problemMessage {
                textHeader(route == .empty ? "Nothing to explain" : "Text too long", quoted: false)
                Text(message).foregroundStyle(Theme.Colors.textSecondary)
            } else if case let .loadedWord(entry) = store.state {
                WordHeaderView(entry: entry) { buttons }
                WordCardView(entry: entry, targetLanguage: store.targetLanguage, showsHeader: false)
            } else {
                textHeader(store.inputText.trimmingCharacters(in: .whitespacesAndNewlines), quoted: route == .sentence)
                TranslateResultView(state: store.state, targetLanguage: store.targetLanguage) { word in
                    Task { await store.lookUp(suggestion: word) }
                }
            }
        } else {
            textHeader("", quoted: false)
            ProgressView()
        }
    }

    /// The header when there is no word card: the selected text (a sentence is quoted, in serif) and the buttons.
    private func textHeader(_ title: String, quoted: Bool) -> some View {
        HStack(alignment: .top, spacing: Theme.Spacing.small) {
            Group {
                if quoted {
                    Text("\u{201C}\(title)\u{201D}")
                        .font(.system(.title3, design: .serif))
                } else {
                    Text(title)
                        .font(.title3.weight(.semibold))
                }
            }
            .foregroundStyle(Theme.Colors.textPrimary)
            .lineLimit(4)
            Spacer()
            HStack(spacing: Theme.Spacing.small) {
                buttons
            }
        }
    }

    // MARK: - Buttons

    /// Listen, Open in Saynata, Close. Icon only (CLAUDE.md "UI rule"). Listen and Open only show when there is something to hear or open.
    @ViewBuilder
    private var buttons: some View {
        if isExplained {
            // Speech is built in F3 (TASK-7). For now the button does nothing.
            IconButton(systemImage: "speaker.wave.2", label: "Listen") {}
        }
        if let url = openURL {
            IconButton(systemImage: "arrow.up.forward.app", label: "Open in Saynata") { onOpenApp(url) }
        }
        IconButton(systemImage: "xmark", label: "Close", action: onClose)
    }

    /// True once a word card or a sentence explanation is on screen.
    private var isExplained: Bool {
        switch store.state {
        case .loaded, .loadedWord: true
        case .idle, .loading, .failed: false
        }
    }

    /// The link for the Open button. Nil until the text is known to be explainable.
    private var openURL: URL? {
        guard let sharedText, TextRoute(text: sharedText).problemMessage == nil else { return nil }
        return DeepLink.lookupURL(text: sharedText.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    // MARK: - Loading

    /// Reads the text, then explains it with the same code path as the app (one token: word; more: sentence).
    private func explainSharedText() async {
        let text = await loadText()
        sharedText = text
        // Text the app cannot explain (empty, over 500 characters) never reaches the backend.
        guard TextRoute(text: text).problemMessage == nil else { return }
        store.inputText = text
        await store.translate()
    }
}

#Preview("Word") {
    ShareSheetView(loadText: { "depend" }, onOpenApp: { _ in }, onClose: {})
}

#Preview("Too long") {
    ShareSheetView(loadText: { String(repeating: "word ", count: 120) }, onOpenApp: { _ in }, onClose: {})
}
