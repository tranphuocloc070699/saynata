//
//  ContentView.swift
//  EnglishTranslation
//

import SwiftUI

struct ContentView: View {
    var body: some View {
        TranslateView()
    }
}

#Preview {
    ContentView()
        .environment(TranslationStore())
        .environment(SpeechPlayer.silent())
        .environment(AccountStore(secrets: NoSecretStore()))
}
