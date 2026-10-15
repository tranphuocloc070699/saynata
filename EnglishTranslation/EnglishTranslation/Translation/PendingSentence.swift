//
//  PendingSentence.swift
//  EnglishTranslation
//

import Foundation

/// A sentence that was just sent and is waiting for Claude (TASK-74).
/// It carries the words found in the bundled dictionary, so the loading screen can show them at once.
/// Like `SentenceResult`, it keeps the sentence and the language from when the request started.
struct PendingSentence: Equatable {
    /// The sentence that was sent (trimmed).
    let sentence: String
    let language: TargetLanguage
    /// The dictionary words found offline, in sentence order. Claude's answer will add its words above these.
    let localWords: [SentenceWordInfo]
}
