//
//  SaynataMacApp.swift
//  SaynataMac
//
//  The Mac app (F6, TASK-85). It lives in the menu bar, with no Dock icon (`LSUIElement` in Info.plist).
//  It compiles the iOS app's folder too (models, network, stores, views), like the extensions do (doc-10).
//

import SwiftUI

@main
struct SaynataMacApp: App {
    var body: some Scene {
        MenuBarExtra("Saynata", systemImage: "character.book.closed") {
            Text("Saynata")
                .padding()
        }
        .menuBarExtraStyle(.window)
    }
}
