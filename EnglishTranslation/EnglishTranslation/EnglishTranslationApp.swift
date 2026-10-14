//
//  EnglishTranslationApp.swift
//  EnglishTranslation
//

import SwiftUI

@main
struct EnglishTranslationApp: App {
    @State private var store = TranslationStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .onOpenURL { url in
                    // The share extension sends `saynata://lookup?text=...`. Put the text in the input and explain it.
                    guard let text = DeepLink.text(from: url) else { return }
                    store.inputText = text
                    Task { await store.translate() }
                }
        }
    }
}
