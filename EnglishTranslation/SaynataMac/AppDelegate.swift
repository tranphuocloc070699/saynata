//
//  AppDelegate.swift
//  SaynataMac
//

import AppKit
import KeyboardShortcuts
import SwiftUI

/// Owns the menu-bar icon, the popover under it (TASK-85.2, mockup option A) and the floating panel.
///
/// Two ways to show the same content:
/// - **Popover** under the icon, for a click on the icon the user can see.
/// - **Floating panel** in the middle near the top of the screen, for the shortcut, the Services item and when the icon
///   is hidden (behind the notch, or pushed off a full menu bar). Anchoring to a hidden icon put the popover in the
///   wrong place on the developer's MacBook.
///
/// It is AppKit (`NSStatusItem` + `NSPopover`), not SwiftUI's `MenuBarExtra`: the shortcut and the Services item must
/// open the window from code and put the cursor in the box, and `MenuBarExtra` has no way to open itself (doc-7).
@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate, NSPopoverDelegate {
    private let store = TranslationStore()
    private let speechPlayer = SpeechPlayer()
    /// The same object the backend client reads the token from, like the iPhone app.
    private let account = AccountStore.shared
    private let liked = LikedStore.live()
    private let focus = MenuBarFocus()

    private var statusItem: NSStatusItem?
    private let popover = NSPopover()
    private var panel: FloatingPanel?
    private let copiedText = CopiedTextWatcher()

    func applicationDidFinishLaunching(_ notification: Notification) {
        let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        item.button?.image = NSImage(systemSymbolName: "character.book.closed", accessibilityDescription: "Saynata")
        item.button?.target = self
        item.button?.action = #selector(iconClicked)
        statusItem = item

        popover.behavior = .transient
        // The app is light mode only (design system rule 4), so the popover's frame and arrow are light too.
        popover.appearance = NSAppearance(named: .aqua)
        popover.delegate = self
        popover.contentSize = MenuBarContentView.size
        popover.contentViewController = makeContentController()

        let panel = FloatingPanel(contentViewController: makeContentController(), size: MenuBarContentView.size)
        panel.onClose = { [weak self] in self?.speechPlayer.stop() }
        self.panel = panel

        // Right-click → Services → Explain with Saynata (TASK-85.4). The item is declared in Info.plist (`NSServices`).
        NSApp.servicesProvider = self
        NSUpdateDynamicServices()

        copiedText.start()

        // The global shortcut (TASK-85.3). It opens the panel from any app, or closes the window that is open.
        KeyboardShortcuts.onKeyUp(for: .openSaynata) { [weak self] in
            self?.toggleFromShortcut()
        }

        #if DEBUG
        // For checking the window without a click: `open SaynataMac.app --args -explain "run late"`.
        if let index = CommandLine.arguments.firstIndex(of: "-explain"), index + 1 < CommandLine.arguments.count {
            explain(CommandLine.arguments[index + 1])
        }
        // `-pressShortcut` acts as if the shortcut was pressed 3 s after start (copy something before that).
        if CommandLine.arguments.contains("-pressShortcut") {
            Task {
                try? await Task.sleep(for: .seconds(3))
                toggleFromShortcut()
            }
        }
        if CommandLine.arguments.contains("-settings") {
            showPanel()
            focus.isSettingsOpen = true
        }
        // `-snapshot` saves the popover as a PNG in the app's temp folder after 6 s (no screen-recording permission needed).
        if CommandLine.arguments.contains("-snapshot") {
            panel.keepsOpen = true
            Task {
                try? await Task.sleep(for: .seconds(6))
                // A sheet (sign-in) is its own window on top of the popover; save that one when it is open.
                let window = panel.isVisible ? panel : popover.contentViewController?.view.window
                guard let view = (window?.attachedSheet ?? window)?.contentView,
                      let image = view.bitmapImageRepForCachingDisplay(in: view.bounds) else { return }
                view.cacheDisplay(in: view.bounds, to: image)
                let file = URL.temporaryDirectory.appending(path: "saynata-popover.png")
                try? image.representation(using: .png, properties: [:])?.write(to: file)
                print("Snapshot: \(file.path(percentEncoded: false))")
                print("Window frame: \(window?.frame ?? .zero), screen: \(window?.screen?.visibleFrame ?? .zero)")
                print("Icon visible: \(isStatusItemVisible)")
            }
        }
        #endif
    }

    /// The same view in each window. Each has its own hosting controller, but they share the store, so the text and
    /// the result are the same in both.
    private func makeContentController() -> NSViewController {
        let controller = NSHostingController(
            rootView: MenuBarContentView()
                .environment(store)
                .environment(speechPlayer)
                .environment(account)
                .environment(liked)
                .environment(focus))
        // The panel's title bar is hidden but still there; without this the content moves down under it.
        controller.safeAreaRegions = []
        return controller
    }

    private var isOpen: Bool { popover.isShown || panel?.isVisible == true }

    private func closeAll() {
        if popover.isShown { popover.performClose(nil) }
        if panel?.isVisible == true { panel?.close() }
    }

    /// A click on the icon: the popover under it. If the icon cannot really be seen, the panel instead.
    @objc func iconClicked() {
        if isOpen {
            closeAll()
        } else if isStatusItemVisible {
            showPopover()
        } else {
            showPanel()
        }
    }

    /// Closes the open window, or opens the panel. Text copied in the last 20 seconds is explained at once
    /// (select, ⌘C, shortcut), for apps like Books that have no Services item. Settings can turn this off.
    private func toggleFromShortcut() {
        if isOpen {
            closeAll()
        } else if UserDefaults.standard.object(forKey: explainsCopiedTextKey) as? Bool ?? true,
                  let text = copiedText.takeFreshText() {
            explain(text)
        } else {
            showPanel()
        }
    }

    /// Opens the popover under the icon, in front of the app the user is in, with the cursor in the box.
    func showPopover() {
        guard let button = statusItem?.button else { return }
        // A menu-bar app is never the active app, so without this the box would not get the keyboard.
        NSApp.activate()
        popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
        popover.contentViewController?.view.window?.makeKey()
        focus.request += 1
    }

    /// Opens the floating panel near the top middle of the screen with the mouse, with the cursor in the box.
    func showPanel() {
        if popover.isShown { popover.performClose(nil) }
        panel?.showCentred()
        focus.request += 1
    }

    /// False when the icon has no window, its window is not on any screen or covered, or it sits behind the notch
    /// (between the two menu-bar areas left and right of the camera, `auxiliaryTopLeftArea` / `auxiliaryTopRightArea`).
    private var isStatusItemVisible: Bool {
        guard let button = statusItem?.button, let window = button.window,
              window.occlusionState.contains(.visible) else { return false }
        let frame = window.convertToScreen(button.convert(button.bounds, to: nil))
        guard let screen = NSScreen.screens.first(where: { $0.frame.contains(NSPoint(x: frame.midX, y: frame.midY)) })
        else { return false }
        if let left = screen.auxiliaryTopLeftArea, let right = screen.auxiliaryTopRightArea,
           frame.maxX > left.maxX, frame.minX < right.minX {
            return false
        }
        return true
    }

    func popoverDidClose(_ notification: Notification) {
        speechPlayer.stop()
    }

    /// Opens the panel and explains `text`, as if the user had typed it.
    func explain(_ text: String) {
        showPanel()
        store.inputText = text
        Task { await store.translate() }
    }

    /// The Services item "Explain with Saynata": macOS calls this with the text the user selected in any app.
    /// The name must match `NSMessage` in Info.plist. It needs no Accessibility permission.
    @objc func explainSelection(
        _ pasteboard: NSPasteboard, userData: String?, error: AutoreleasingUnsafeMutablePointer<NSString?>
    ) {
        let text = pasteboard.string(forType: .string)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !text.isEmpty else {
            error.pointee = "No text was selected." as NSString
            return
        }
        explain(text)
    }
}

/// What the app delegate asks of the popover's view: put the cursor in the box (the view watches `request`),
/// or show Settings.
@Observable
final class MenuBarFocus {
    var request = 0
    var isSettingsOpen = false
}
