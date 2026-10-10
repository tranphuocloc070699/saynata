//
//  Explanation.swift
//  EnglishTranslation
//

import Foundation

/// What the model returns for one sentence. Decoded from the model's JSON reply.
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

    /// Decodes the model's reply. Models sometimes wrap JSON in ```json fences or add a
    /// sentence around it, so this keeps only the part from the first `{` to the last `}`.
    static func decode(fromModelReply reply: String) throws -> Explanation {
        guard let start = reply.firstIndex(of: "{"),
              let end = reply.lastIndex(of: "}"),
              start < end
        else {
            throw LLMError.badFormat
        }
        let json = Data(reply[start...end].utf8)
        do {
            return try JSONDecoder().decode(Explanation.self, from: json)
        } catch {
            throw LLMError.badFormat
        }
    }
}
