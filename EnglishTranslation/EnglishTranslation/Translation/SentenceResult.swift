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
    /// One entry per word in `explanation.words`, with the pill and chip text for its card.
    let words: [SentenceWordInfo]

    /// - Parameter bundledEntry: Looks a word up in the bundled dictionary. Nil entries are fine.
    init(
        sentence: String,
        explanation: Explanation,
        language: TargetLanguage,
        bundledEntry: (String) -> WordEntry? = { _ in nil }
    ) {
        self.sentence = sentence
        self.explanation = explanation
        self.language = language
        self.words = explanation.words.map { word in
            SentenceWordInfo(word: word, entry: bundledEntry(word.word))
        }
    }
}
