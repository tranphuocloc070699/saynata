//
//  MacSettingsSections.swift
//  SaynataMac
//

import KeyboardShortcuts
import ServiceManagement
import SwiftUI

extension KeyboardShortcuts.Name {
    /// Opens (or closes) the popover from any app (TASK-85.3). Default ⌥⇧Space; the user can change it in Settings.
    static let openSaynata = Self("openSaynata", default: .init(.space, modifiers: [.option, .shift]))
}

/// The Mac-only part of Settings, shown after the shared sections.
struct MacSettingsSections: View {
    /// Read from macOS each time Settings opens, so a change in System Settings > Login Items shows here too.
    @State private var opensAtLogin = SMAppService.mainApp.status != .notRegistered
    @State private var loginItemError: String?

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

        Section {
            // A switch row with its title, not a button (CLAUDE.md "UI rule").
            Toggle("Open at login", isOn: $opensAtLogin)
                .toggleStyle(.switch)
                .tint(Theme.Colors.accent)
                .frame(minHeight: Theme.minTapTarget)
                .onChange(of: opensAtLogin) { _, isOn in setOpensAtLogin(isOn) }
        } header: {
            Text("Start")
        } footer: {
            if let loginItemError {
                Label(loginItemError, systemImage: "exclamationmark.circle")
                    .foregroundStyle(Theme.Colors.error)
            }
        }
        .listRowBackground(Theme.Colors.surface)
    }

    /// Adds or removes Saynata from the login items (TASK-85.4), with Apple's `SMAppService` (macOS 13+).
    private func setOpensAtLogin(_ isOn: Bool) {
        loginItemError = nil
        do {
            if isOn {
                try SMAppService.mainApp.register()
            } else {
                try SMAppService.mainApp.unregister()
            }
            // macOS may want the user to allow it first.
            if SMAppService.mainApp.status == .requiresApproval {
                loginItemError = "Allow Saynata in System Settings > General > Login Items."
            }
        } catch {
            loginItemError = error.localizedDescription
        }
    }
}
