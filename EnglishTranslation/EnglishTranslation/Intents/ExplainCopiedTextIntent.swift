//
//  ExplainCopiedTextIntent.swift
//  EnglishTranslation
//

import AppIntents
import UIKit

/// "Explain copied text" (TASK-87): copy text in any app, then Back Tap or the Action Button runs this. Saynata opens,
/// pastes the copied text into the input and explains it; with nothing copied, the input is empty and has the keyboard.
///
/// App target only: the extensions and the Mac app leave the `Intents/` folder out (project exceptions).
struct ExplainCopiedTextIntent: AppIntent {
    static let title: LocalizedStringResource = "Explain copied text"
    static let description = IntentDescription(
        "Opens Saynata with the text you copied, explained in simple English.")
    /// The app must be on screen: iOS only asks "Allow Paste" for an app it can show.
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        CopiedTextLaunch.shared.request()
        return .result()
    }
}

/// The real clipboard. Reading it shows iOS's "Allow Paste" question, unless the user set
/// Settings > Apps > Saynata > Paste from Other Apps to Allow (the row appears after the first question).
struct SystemClipboard: ClipboardReading {
    func content() async -> ClipboardContent {
        await MainActor.run {
            // `hasStrings` never asks for permission, so an empty clipboard never shows the question.
            guard UIPasteboard.general.hasStrings else { return .empty }
            // Text is there but `string` is nil: the user tapped Don't Allow Paste.
            guard let text = UIPasteboard.general.string else { return .notAllowed }
            return .text(text)
        }
    }
}
