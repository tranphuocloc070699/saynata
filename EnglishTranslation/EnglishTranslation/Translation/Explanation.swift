//
//  Explanation.swift
//  EnglishTranslation
//

import Foundation

/// What the backend returns for one sentence.
struct Explanation: Codable, Equatable, Sendable {
    /// The sentence explained in simple English.
    let meaning: String
    /// Difficult words or phrases. Empty when there are none.
    let words: [Word]
    /// The text with its real mistakes fixed (TASK-110, prompt v10). Nil when the text is already natural,
    /// and for answers saved before v10.
    var better: String? = nil
    /// One short line in simple English that says why `better` is better. Nil when there is no fix.
    var betterWhy: String? = nil
    /// The whole sentence in the target language.
    let translation: String

    /// One difficult word or phrase from the sentence.
    struct Word: Codable, Equatable, Sendable, Identifiable {
        /// The word as it appears in the sentence, for example "drew out".
        let word: String
        /// For example "noun", "adjective", "phrasal verb".
        let partOfSpeech: String
        /// A short explanation in simple English.
        let meaning: String

        /// The word itself is unique enough inside one sentence.
        var id: String { word }
    }
}
