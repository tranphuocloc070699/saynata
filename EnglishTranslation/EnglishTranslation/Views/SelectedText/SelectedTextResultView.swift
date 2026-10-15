//
//  SelectedTextResultView.swift
//  EnglishTranslation
//

import SwiftUI

/// The sheet shown over Safari, Messages and other apps when the user sends selected text to Saynata.
/// Two extensions use it: the share extension (Share, then Saynata) and the translation extension
/// (the system "Translate" item, when Saynata is the default translation app).
///
/// A header row (the headword with its IPA, or the quoted sentence) with icon buttons on the right:
/// Listen, Open in Saynata (only when `onOpenApp` is set), Close. Below it the app's own views, as they are:
/// `WordCardView` for a word, `TranslateResultView` for a sentence, so this sheet picks up their style when they change.
struct SelectedTextResultView: View {
    /// Reads the selected text from the host app. Called once, when the sheet appears.
    let loadText: () async -> String
    /// Opens the Saynata app on this link (`saynata://lookup?text=...`).
    /// Nil when the extension cannot open the app (the translation extension): the Open button is then hidden.
    var onOpenApp: ((URL) -> Void)?
    /// Closes the sheet. Nil when the host draws its own close button (the translation extension): the Close button is then hidden.
    var onClose: (() -> Void)?

    @State private var store = TranslationStore()
    /// The extensions have no app around them, so the sheet makes its own player for the speakers (F3).
    @State private var speechPlayer = SpeechPlayer()
    /// Nil while the text is still being read.
    @State private var selectedText: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Spacing.large) {
                content
            }
            .padding(Theme.Spacing.large)
        }
        .background(Theme.Colors.background)
        .environment(speechPlayer)
        .task { await explainSelectedText() }
        .onDisappear { speechPlayer.stop() }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        if let selectedText {
            let route = TextRoute(text: selectedText)
            if let message = route.problemMessage {
                textHeader(route == .empty ? "Nothing to explain" : "Text too long", quoted: false)
                Text(message).foregroundStyle(Theme.Colors.textSecondary)
            } else if case let .loadedWord(entry) = store.state {
                WordHeaderView(entry: entry, isSpeaking: speechPlayer.currentID == headerSpeechID) { buttons }
                WordCardView(entry: entry, targetLanguage: store.targetLanguage, showsHeader: false)
            } else {
                textHeader(store.inputText.trimmingCharacters(in: .whitespacesAndNewlines), quoted: route == .sentence)
                TranslateResultView(state: store.state, targetLanguage: store.targetLanguage) { word in
                    Task { await store.lookUp(suggestion: word) }
                }
                // The extensions cannot share the app's sign-in (a free Apple ID has no shared Keychain group),
                // so they are guests. They send the user to the app to sign in (doc-17, known gaps).
                if store.textNeedingSignIn != nil {
                    Text("Sign in from the Saynata app to explain new text.")
                        .font(.subheadline)
                        .foregroundStyle(Theme.Colors.textSecondary)
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
        if isExplained, let headerText {
            let isPlaying = speechPlayer.currentID == headerSpeechID
            IconButton(
                systemImage: isPlaying ? "stop.fill" : "speaker.wave.2",
                label: isPlaying ? "Stop" : "Listen"
            ) {
                if isPlaying {
                    speechPlayer.stop()
                } else {
                    speechPlayer.speak(headerText, id: headerSpeechID)
                }
            }
        }
        if let onOpenApp, let url = openURL {
            IconButton(systemImage: "arrow.up.forward.app", label: "Open in Saynata") { onOpenApp(url) }
        }
        if let onClose {
            IconButton(systemImage: "xmark", label: "Close", action: onClose)
        }
    }

    private let headerSpeechID = "selected-text"

    /// What the header speaker reads: the word as shown, or the selected sentence.
    private var headerText: String? {
        switch store.state {
        case let .loadedWord(entry): entry.matchedForm?.text ?? entry.word
        case .loaded: store.inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        case .idle, .loading, .meaning, .failed: nil
        }
    }

    /// True once a word card or a sentence explanation is on screen.
    private var isExplained: Bool {
        switch store.state {
        case .loaded, .loadedWord: true
        case .idle, .loading, .meaning, .failed: false
        }
    }

    /// The link for the Open button. Nil until the text is known to be explainable.
    private var openURL: URL? {
        guard let selectedText, TextRoute(text: selectedText).problemMessage == nil else { return nil }
        return DeepLink.lookupURL(text: selectedText.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    // MARK: - Loading

    /// Reads the text, then explains it with the same code path as the app (one token: word; more: sentence).
    private func explainSelectedText() async {
        let text = await loadText()
        selectedText = text
        // Text the app cannot explain (empty, over 500 characters) never reaches the backend.
        guard TextRoute(text: text).problemMessage == nil else { return }
        store.inputText = text
        await store.translate()
    }
}

#Preview("Word") {
    SelectedTextResultView(loadText: { "depend" }, onOpenApp: { _ in }, onClose: {})
}

#Preview("Too long") {
    SelectedTextResultView(loadText: { String(repeating: "word ", count: 120) }, onOpenApp: { _ in }, onClose: {})
}
