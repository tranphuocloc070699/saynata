//
//  SentenceResult.swift
//  EnglishTranslation
//

import Foundation

/// A finished sentence answer, kept together with what it was made from.
/// The sentence and the language are saved when the request starts, so changing the picker or
/// the text box later does not change a result that is already on screen (TASK-14).
struct SentenceResult: Equatable {
    /// The sentence that was sent (trimmed).
    let sentence: String
    let explanation: Explanation
    /// The language the translation was made in. The result heading uses this, not the live picker.
    let language: TargetLanguage
}
