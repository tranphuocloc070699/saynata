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
        }
    }
}
