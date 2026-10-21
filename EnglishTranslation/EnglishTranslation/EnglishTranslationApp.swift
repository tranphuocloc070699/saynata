//
//  EnglishTranslationApp.swift
//  EnglishTranslation
//

import SwiftUI

@main
struct EnglishTranslationApp: App {
    @State private var store = TranslationStore()
    @State private var speechPlayer = SpeechPlayer()
    /// The same object the backend client reads the token from (`TranslatorFactory`), so a sign-in shows everywhere.
    @State private var account = AccountStore.shared
    /// The liked words and sentences (TASK-93.2).
    @State private var liked = LikedStore.live()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .environment(speechPlayer)
                .environment(account)
                .environment(liked)
                // Open the backend connection now, so the first lookup does not pay for it (TASK-83.5).
                .task { await TranslatorFactory.warmUpConnection() }
                // Stop reading when the app is sent to the background (TASK-46).
                .onChange(of: scenePhase) {
                    if scenePhase == .background { speechPlayer.stop() }
                }
                .onOpenURL { url in
                    // The emailed sign-in link (TASK-92): `saynata://sign-in?token=...`, or later the https link.
                    if let token = DeepLink.signInToken(from: url) {
                        Task { await account.completeLinkSignIn(token: token) }
                        return
                    }
                    // The share extension sends `saynata://lookup?text=...`. Put the text in the input and explain it.
                    guard let text = DeepLink.text(from: url) else { return }
                    store.inputText = text
                    Task { await store.translate() }
                }
                .alert(
                    "Could not sign in",
                    isPresented: Binding(
                        get: { account.linkSignInError != nil },
                        set: { if !$0 { account.clearLinkSignInError() } }),
                    actions: { Button("OK", role: .cancel) {} },
                    message: { Text(account.linkSignInError ?? "") })
        }
    }
}
