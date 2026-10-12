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
