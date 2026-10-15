//
//  SentenceMeaning.swift
//  EnglishTranslation
//

import Foundation

/// The first part of a sentence answer, shown while the words and the translation are still being written (TASK-65).
/// Like `SentenceResult`, it keeps the sentence and the language from when the request started.
struct SentenceMeaning: Equatable {
    /// The sentence that was sent (trimmed).
    let sentence: String
    /// The sentence explained in simple English.
    let meaning: String
    /// The language the final result will have.
    let language: TargetLanguage
}
