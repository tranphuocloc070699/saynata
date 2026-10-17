//
//  ExplainCopiedTextIntent.swift
//  EnglishTranslation
//

import AppIntents
import SwiftUI
import UIKit

/// "Explain copied text" (TASK-87): copy text in any app, then Back Tap or the Action Button runs this, and a
/// Shortcuts card shows the explanation on top of that app. Saynata does not open.
///
/// App target only: the extensions and the Mac app leave the `Intents/` folder out (project exceptions).
struct ExplainCopiedTextIntent: AppIntent {
    static let title: LocalizedStringResource = "Explain copied text"
    static let description = IntentDescription(
        "Shows the simple English meaning of the text you copied, on top of the app you are in.")
    /// The card shows over the current app; the "Open in Saynata" link on it opens the full result.
    static let openAppWhenRun = false

    /// A shortcut can pass text (for example "Get text from input"). Empty: the clipboard is read.
    @Parameter(title: "Text")
    var text: String?

    @MainActor
    func perform() async throws -> some IntentResult & ShowsSnippetView {
        let explainer = CopiedTextExplainer(
            clipboard: SystemClipboard(),
            makeWordLookup: { try TranslatorFactory.makeWordLookup() },
            makeTranslator: { try TranslatorFactory.makeTranslator() })
        let result = await explainer.explain(text)
        return .result(view: CopiedTextCard(result: result))
    }
}

/// The real clipboard. Reading it may show iOS's "Allow Paste" question, unless the user set
/// Settings > Saynata > Paste from Other Apps to Allow.
struct SystemClipboard: ClipboardReading {
    func text() async -> String? {
        await MainActor.run {
            // `hasStrings` does not ask for permission, so an empty clipboard never shows the question.
            UIPasteboard.general.hasStrings ? UIPasteboard.general.string : nil
        }
    }
}
