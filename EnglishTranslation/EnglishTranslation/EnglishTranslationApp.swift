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
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .environment(speechPlayer)
                .environment(account)
                // Stop reading when the app is sent to the background (TASK-46).
                .onChange(of: scenePhase) {
                    if scenePhase == .background { speechPlayer.stop() }
                }
                .onOpenURL { url in
                    // The share extension sends `saynata://lookup?text=...`. Put the text in the input and explain it.
                    guard let text = DeepLink.text(from: url) else { return }
                    store.inputText = text
                    Task { await store.translate() }
                }
        }
    }
}
