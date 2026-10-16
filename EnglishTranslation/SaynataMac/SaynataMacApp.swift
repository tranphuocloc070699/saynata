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
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        // The menu-bar icon and its popover are made by the app delegate. A SwiftUI app needs one scene,
        // and an empty Settings scene opens no window.
        Settings {
            EmptyView()
        }
    }
}
