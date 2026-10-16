//
//  CopiedTextWatcher.swift
//  SaynataMac
//

import AppKit

/// Lets the shortcut explain what the user just copied (for Apple Books and any app without a Services item):
/// select, ⌘C, ⌥⇧Space.
///
/// macOS gives no time for a clipboard change, so a light timer notes when the clipboard's change count moves. Only
/// the count is read every half second, never the text. The text is read once, when the shortcut is pressed, and
/// only when the copy is fresh (the last 20 seconds) and not Saynata's own Copy button.
/// No permission is needed: this is the same as a paste.
@MainActor
final class CopiedTextWatcher {
    /// How old a copy may be and still count as "just copied".
    static let freshness: TimeInterval = 20

    private var lastCount = NSPasteboard.general.changeCount
    private var changedAt: Date?
    private var timer: Timer?

    func start() {
        let timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.noteChange() }
        }
        // Lets macOS group the timer with other work, to save energy.
        timer.tolerance = 0.25
        self.timer = timer
    }

    /// The text copied in the last 20 seconds, cleaned (`CopiedText`), or nil. A copy is used only once.
    func takeFreshText() -> String? {
        noteChange()
        guard let changedAt, Date.now.timeIntervalSince(changedAt) <= Self.freshness else { return nil }
        self.changedAt = nil
        guard let copied = NSPasteboard.general.string(forType: .string) else { return nil }
        let text = CopiedText.cleaned(copied)
        return text.isEmpty ? nil : text
    }

    private func noteChange() {
        let count = NSPasteboard.general.changeCount
        guard count != lastCount else { return }
        lastCount = count
        // Saynata's own Copy button is not "the user copied something to explain".
        changedAt = count == Pasteboard.ownChangeCount ? nil : .now
    }
}
