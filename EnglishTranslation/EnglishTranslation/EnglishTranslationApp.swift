//
//  EnglishTranslationApp.swift
//  EnglishTranslation
//

import SwiftUI

@main
struct EnglishTranslationApp: App {
    @State private var store = TranslationStore()
    @State private var speechPlayer = SpeechPlayer()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .environment(speechPlayer)
        }
    }
}
