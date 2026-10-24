//
//  SentenceResult.swift
//  EnglishTranslation
//

import Foundation

/// A finished sentence answer, kept together with what it was made from.
/// The sentence and the language are saved when the request starts, so changing the picker or
/// the text box later does not change a result that is already on screen (TASK-14).
/// Since TASK-89 the answer is the sentence, its simple summary (`explanation.meaning`) and the translation.
/// The words are not listed any more: every word of both sentences can be tapped instead.
struct SentenceResult: Equatable {
    /// The sentence that was sent (trimmed).
    let sentence: String
    let explanation: Explanation
    /// The language the translation was made in. The result heading uses this, not the live picker.
    let language: TargetLanguage
    /// False when the text came from another app (copied text, Share, the Mac Services item): it is someone
    /// else's text, so the "Better way to say it" row is hidden (TASK-110).
    var isTyped = true
}
