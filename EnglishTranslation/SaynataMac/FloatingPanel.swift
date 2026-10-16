//
//  FloatingPanel.swift
//  SaynataMac
//

import AppKit

/// The Spotlight-style window (fix after the developer's first Mac test): the same content as the popover, centred
/// near the top of the screen the mouse is on. The shortcut and the Services item open it, and so does a click when
/// the menu-bar icon cannot be seen (behind the notch, or pushed off by other icons).
///
/// It is a non-activating panel, so the app the user is in stays in front, but the panel still takes the keyboard.
/// Esc or a click outside closes it.
final class FloatingPanel: NSPanel {
    /// Runs after the panel closed (the app delegate stops speech there).
    var onClose: (() -> Void)?
    /// Debug checks (`-snapshot`) keep the panel open when another app takes the keyboard.
    var keepsOpen = false

    init(contentViewController: NSViewController, size: CGSize) {
        super.init(
            contentRect: NSRect(origin: .zero, size: size),
            styleMask: [.nonactivatingPanel, .titled, .fullSizeContentView],
            backing: .buffered,
            defer: true)
        self.contentViewController = contentViewController
        setContentSize(size)
        titleVisibility = .hidden
        titlebarAppearsTransparent = true
        for button in [NSWindow.ButtonType.closeButton, .miniaturizeButton, .zoomButton] {
            standardWindowButton(button)?.isHidden = true
        }
        isMovableByWindowBackground = true
        isFloatingPanel = true
        level = .floating
        hidesOnDeactivate = false
        isReleasedWhenClosed = false
        // Shows over a full-screen app and on every desktop.
        collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        // Light mode only (design system rule 4).
        appearance = NSAppearance(named: .aqua)

        NotificationCenter.default.addObserver(
            self, selector: #selector(didResignKey), name: NSWindow.didResignKeyNotification, object: self)
    }

    /// A borderless-looking panel must say yes, or the text box never gets the keyboard.
    override var canBecomeKey: Bool { true }

    /// Esc closes the panel (a sheet on top handles its own Esc first).
    override func cancelOperation(_ sender: Any?) {
        close()
    }

    override func close() {
        super.close()
        onClose?()
    }

    /// Shows the panel centred on the screen with the mouse, about a sixth of the way down, and gives it the keyboard.
    func showCentred() {
        let mouse = NSEvent.mouseLocation
        let screen = NSScreen.screens.first { NSMouseInRect(mouse, $0.frame, false) } ?? NSScreen.main
        if let area = screen?.visibleFrame {
            let size = frame.size
            let top = area.maxY - area.height / 6
            let y = max(area.minY, top - size.height)
            setFrameOrigin(NSPoint(x: area.midX - size.width / 2, y: y))
        }
        makeKeyAndOrderFront(nil)
    }

    /// A click outside closes the panel, like the popover. Not while a sheet (sign-in, Settings) is open on it:
    /// the sheet takes the keyboard, and the panel must stay under it.
    @objc private func didResignKey() {
        guard isVisible, attachedSheet == nil, !keepsOpen else { return }
        close()
    }
}
