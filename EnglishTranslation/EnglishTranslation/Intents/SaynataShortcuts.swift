//
//  SaynataShortcuts.swift
//  EnglishTranslation
//

import AppIntents

/// The App Shortcuts iOS lists for Saynata with no setup: in the Shortcuts app, in Spotlight, in Siri, and in the
/// Action Button's Shortcut list. Back Tap and its setup are in doc-21.
struct SaynataShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ExplainCopiedTextIntent(),
            phrases: [
                "Explain copied text in \(.applicationName)",
                "Explain with \(.applicationName)",
            ],
            shortTitle: "Explain copied text",
            systemImageName: "text.magnifyingglass")
    }
}
