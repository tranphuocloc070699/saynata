//
//  MacSettingsSections.swift
//  SaynataMac
//

import KeyboardShortcuts
import SwiftUI

extension KeyboardShortcuts.Name {
    /// Opens (or closes) the popover from any app (TASK-85.3). Default ⌥⇧Space; the user can change it in Settings.
    static let openSaynata = Self("openSaynata", default: .init(.space, modifiers: [.option, .shift]))
}

/// The Mac-only part of Settings, shown after the shared sections.
struct MacSettingsSections: View {
    var body: some View {
        Section {
            // The recorder's label is a row title, not a button label (CLAUDE.md "UI rule").
            KeyboardShortcuts.Recorder("Open Saynata", name: .openSaynata)
                .frame(minHeight: Theme.minTapTarget)
        } header: {
            Text("Shortcut")
        } footer: {
            Text("Works in every app. Press it again or Esc to close.")
        }
        .listRowBackground(Theme.Colors.surface)
    }
}
