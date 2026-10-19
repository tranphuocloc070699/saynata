//
//  TranslateView.swift
//  EnglishTranslation
//

import SwiftUI

/// The main screen. Only puts the subviews together; each part lives in `Subviews/`.
struct TranslateView: View {
    @Environment(TranslationStore.self) private var store
    @Environment(SpeechPlayer.self) private var speechPlayer
    @Environment(AccountStore.self) private var account
    @Environment(LikedStore.self) private var liked
    /// True while the text box has the keyboard (TASK-50).
    @FocusState private var isInputFocused: Bool
    /// Back Tap / the Action Button asks to start from the clipboard (TASK-87).
    private let launch = CopiedTextLaunch.shared

    var body: some View {
        @Bindable var store = store

        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                TranslateOptionsView(targetLanguage: $store.targetLanguage)
                    .simultaneousGesture(hideKeyboardTap)
                TranslateInputView(isFocused: $isInputFocused)
                TranslateResultView(
                    state: store.state,
                    targetLanguage: store.targetLanguage,
                    pendingSentence: store.pendingSentence,
                    onSuggestionTap: { word in Task { await store.lookUp(suggestion: word) } },
                    loadWordEntry: { word in try await store.wordEntry(for: word) })
                    // At least this tall, so the empty space under a short result also hides the keyboard.
                    .frame(maxWidth: .infinity, minHeight: 400, alignment: .topLeading)
                    .contentShape(Rectangle())
                    .simultaneousGesture(hideKeyboardTap)
            }
            .padding()
        }
        // Drag the result to hide the keyboard.
        .scrollDismissesKeyboard(.interactively)
        // A new search or a new result replaces what is on screen, so the old speech must stop (TASK-46).
        .onChange(of: store.state.resultKey) { speechPlayer.stop() }
        // "Explain copied text" opened the app: paste and explain, or an empty input with the keyboard.
        // `task(id:)` also runs when the shortcut started the app, since the count is already 1 then.
        .task(id: launch.requestCount) {
            guard launch.requestCount > 0 else { return }
            let hadSignInSheet = store.isSignInSheetOpen
            let wantsKeyboard = await store.startFromClipboard(SystemClipboard())
            guard wantsKeyboard else { return }
            // A closing sheet takes the focus with it, so wait until it is gone.
            if hadSignInSheet { try? await Task.sleep(for: .milliseconds(500)) }
            isInputFocused = true
        }
        // The backend said "sign in" (TASK-70). The sheet opens over the result; when the code is right it closes
        // and the same request runs again.
        .sheet(isPresented: $store.isSignInSheetOpen, onDismiss: { liked.cancelPending() }) {
            SignInSheet(account: account) {
                await store.resumeAfterSignIn()
                await liked.signedIn()
            }
        }
        // A guest tapped the star (TASK-93.2): the same sign-in sheet opens, and the like is sent after sign-in.
        .onChange(of: liked.signInRequested) { _, isRequested in
            guard isRequested else { return }
            liked.signInRequested = false
            store.isSignInSheetOpen = true
        }
        // Signed in: load the liked list. Signed out: it belongs to the account, so it goes from this phone.
        .task(id: account.user) {
            if account.isSignedIn { await liked.refresh() } else { liked.clear() }
        }
    }

    /// A tap outside the text box hides the keyboard. It is "simultaneous", so buttons, folded rows,
    /// speakers and text selection still get the same tap. It is not on the text box itself:
    /// a tap there must keep the keyboard.
    private var hideKeyboardTap: some Gesture {
        TapGesture().onEnded { isInputFocused = false }
    }
}

#Preview {
    TranslateView()
        .environment(TranslationStore())
        .environment(SpeechPlayer.silent())
        .environment(AccountStore(secrets: NoSecretStore()))
        .environment(LikedStore.live())
}
