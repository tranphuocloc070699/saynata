//
//  QuickExplainIntent.swift
//  EnglishTranslation
//

import AppIntents
import SwiftUI

/// "Quick explain" (TASK-90): Back Tap, the Action Button or Siri runs this over the app you are in, for example a
/// full-screen video. iOS asks for the text in its own small box (type, paste or speak it), and the answer shows as a
/// card on top of that app. Saynata does not open.
///
/// The text is a parameter, not the clipboard: a background App Intent cannot ask "Allow Paste" (TASK-87, doc-21).
/// App target only: the extensions and the Mac app leave the `Intents/` folder out (project exceptions).
struct QuickExplainIntent: AppIntent {
    static let title: LocalizedStringResource = "Quick explain"
    static let description = IntentDescription(
        "Type or paste a word or sentence and see its simple English meaning on top of the app you are in.")
    /// The card shows over the current app; the "Open in Saynata" link on it opens the full result.
    static let openAppWhenRun = false

    @Parameter(
        title: "Text",
        inputOptions: String.IntentInputOptions(keyboardType: .default, capitalizationType: .none, autocorrect: false),
        requestValueDialog: IntentDialog("What do you want to explain?"))
    var text: String

    static var parameterSummary: some ParameterSummary {
        Summary("Explain \(\.$text)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ShowsSnippetView {
        let explainer = QuickExplainer(
            makeWordLookup: { try TranslatorFactory.makeWordLookup() },
            makeTranslator: { try TranslatorFactory.makeTranslator() },
            language: TargetLanguagePreference.shared.language)
        let result = await explainer.explain(text)
        let line = await explainer.languageLine(for: result)
        return .result(view: QuickExplainCard(result: result, language: explainer.language, wordLine: line))
    }
}
