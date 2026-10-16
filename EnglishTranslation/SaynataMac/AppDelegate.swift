//
//  AppDelegate.swift
//  SaynataMac
//

import AppKit
import KeyboardShortcuts
import SwiftUI

/// Owns the menu-bar icon and the popover under it (TASK-85.2, mockup option A).
///
/// It is AppKit (`NSStatusItem` + `NSPopover`), not SwiftUI's `MenuBarExtra`: the shortcut and the Services item must
/// open the window from code and put the cursor in the box, and `MenuBarExtra` has no way to open itself (doc-7).
@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate, NSPopoverDelegate {
    private let store = TranslationStore()
    private let speechPlayer = SpeechPlayer()
    /// The same object the backend client reads the token from, like the iPhone app.
    private let account = AccountStore.shared
    private let focus = MenuBarFocus()

    private var statusItem: NSStatusItem?
    private let popover = NSPopover()

    func applicationDidFinishLaunching(_ notification: Notification) {
        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        item.button?.image = NSImage(systemSymbolName: "character.book.closed", accessibilityDescription: "Saynata")
        item.button?.target = self
        item.button?.action = #selector(togglePopover)
        statusItem = item

        popover.behavior = .transient
        // The app is light mode only (design system rule 4), so the popover's frame and arrow are light too.
        popover.appearance = NSAppearance(named: .aqua)
        popover.delegate = self
        popover.contentSize = MenuBarContentView.size
        popover.contentViewController = NSHostingController(
            rootView: MenuBarContentView()
                .environment(store)
                .environment(speechPlayer)
                .environment(account)
                .environment(focus))

        // The global shortcut (TASK-85.3). It opens the popover from any app, or closes it when it is open.
        KeyboardShortcuts.onKeyUp(for: .openSaynata) { [weak self] in
            self?.togglePopover()
        }

        #if DEBUG
        // For checking the window without a click: `open SaynataMac.app --args -explain "run late"`.
        if let index = CommandLine.arguments.firstIndex(of: "-explain"), index + 1 < CommandLine.arguments.count {
            showPopover()
            store.inputText = CommandLine.arguments[index + 1]
            Task { await store.translate() }
        }
        if CommandLine.arguments.contains("-settings") {
            showPopover()
            focus.isSettingsOpen = true
        }
        // `-snapshot` saves the popover as a PNG in the app's temp folder after 6 s (no screen-recording permission needed).
        if CommandLine.arguments.contains("-snapshot") {
            Task {
                try? await Task.sleep(for: .seconds(6))
                // A sheet (sign-in) is its own window on top of the popover; save that one when it is open.
                let window = popover.contentViewController?.view.window
                guard let view = (window?.attachedSheet ?? window)?.contentView,
                      let image = view.bitmapImageRepForCachingDisplay(in: view.bounds) else { return }
                view.cacheDisplay(in: view.bounds, to: image)
                let file = URL.temporaryDirectory.appending(path: "saynata-popover.png")
                try? image.representation(using: .png, properties: [:])?.write(to: file)
                print("Snapshot: \(file.path(percentEncoded: false))")
            }
        }
        #endif
    }

    @objc func togglePopover() {
        if popover.isShown {
            popover.performClose(nil)
        } else {
            showPopover()
        }
    }

    /// Opens the popover in front of the app the user is in, with the cursor in the box.
    func showPopover() {
        guard let button = statusItem?.button else { return }
        // A menu-bar app is never the active app, so without this the box would not get the keyboard.
        NSApp.activate()
        popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
        popover.contentViewController?.view.window?.makeKey()
        focus.request += 1
    }

    func popoverDidClose(_ notification: Notification) {
        speechPlayer.stop()
    }
}

/// What the app delegate asks of the popover's view: put the cursor in the box (the view watches `request`),
/// or show Settings.
@Observable
final class MenuBarFocus {
    var request = 0
    var isSettingsOpen = false
}
