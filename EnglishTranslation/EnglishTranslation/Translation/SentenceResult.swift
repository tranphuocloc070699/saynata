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
    /// Claude's words first (one per word in `explanation.words`), then the other dictionary words
    /// found in the sentence. Each has the pill and chip text for its card.
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
        // Claude's words first, with Claude's meaning. Then the other words from the bundled dictionary (TASK-56).
        let claudeWords = explanation.words.map { word in
            SentenceWordInfo(word: word, entry: bundledEntry(word.word))
        }
        self.words = claudeWords + SentenceWordFinder.extraWords(
            in: sentence, skipping: claudeWords, bundledEntry: bundledEntry)
    }
}
