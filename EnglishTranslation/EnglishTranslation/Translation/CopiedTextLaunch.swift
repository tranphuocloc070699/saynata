//
//  CopiedTextLaunch.swift
//  EnglishTranslation
//

import Foundation
import Observation

/// Anything that can read the text on the clipboard. `SystemClipboard` (UIKit) is the real one; tests use a fake.
protocol ClipboardReading: Sendable {
    func content() async -> ClipboardContent
}

/// What the clipboard gave.
enum ClipboardContent: Equatable, Sendable {
    case text(String)
    /// No text was copied.
    case empty
    /// There is text, but iOS did not let Saynata read it ("Don't Allow Paste").
    case notAllowed
}

/// "Explain copied text" (TASK-87): Back Tap or the Action Button runs `ExplainCopiedTextIntent`, which opens the
/// app and calls `request()`. The main screen watches `requestCount` and starts from the clipboard
/// (`TranslationStore.startFromClipboard`).
///
/// The app reads the clipboard itself, on screen: iOS only asks "Allow Paste" for an app it can show. Read from the
/// background (the first version, a Shortcuts card), the paste was refused without a question.
@Observable
@MainActor
final class CopiedTextLaunch {
    static let shared = CopiedTextLaunch()

    /// Goes up by one on every request, so two Back Taps in a row are both seen.
    private(set) var requestCount = 0

    func request() {
        requestCount += 1
    }
}
