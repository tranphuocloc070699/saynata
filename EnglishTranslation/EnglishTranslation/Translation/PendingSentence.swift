//
//  PendingSentence.swift
//  EnglishTranslation
//

import Foundation

/// A sentence that was just sent and is waiting for Claude (TASK-74).
/// The loading screen shows it at once, in the same card as the result, with a spinner where the summary will be.
/// Like `SentenceResult`, it keeps the sentence and the language from when the request started.
struct PendingSentence: Equatable {
    /// The sentence that was sent (trimmed).
    let sentence: String
    let language: TargetLanguage
}
