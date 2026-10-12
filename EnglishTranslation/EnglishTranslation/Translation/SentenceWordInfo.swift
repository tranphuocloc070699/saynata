//
//  SentenceWordInfo.swift
//  EnglishTranslation
//

import Foundation

/// What one word card in a sentence result shows: the word, a form pill, a level chip and a short explanation.
///
/// The pill and the chip come from the bundled dictionary when the word is there. If it is not,
/// the pill uses the backend's "base form: X" note and there is no chip.
struct SentenceWordInfo: Equatable, Identifiable {
    /// The word as it appears in the sentence, for example "blurred".
    let word: String
    /// "past tense of blur" (dictionary) or "base form: draw out" (backend note). Nil when there is no form.
    let pillText: String?
    /// "verb · B2". Only when the word is in the bundled dictionary.
    let chipText: String?
    /// The backend's short explanation, without the "base form" note (the pill shows it).
    let explanation: String
    /// The bundled dictionary entry, when the word is there. Opening the word card uses it.
    let entry: WordEntry?

    var id: String { word }

    /// A single word can open its full word card. A phrase like "drew out" cannot.
    var canOpenWordCard: Bool {
        CacheKeys.isSingleToken(word)
    }

    /// - Parameters:
    ///   - word: One word from the backend answer.
    ///   - entry: The bundled dictionary entry for it, or nil when the dictionary does not have it.
    init(word: Explanation.Word, entry: WordEntry?) {
        let (explanation, baseFormNote) = Self.splitBaseFormNote(from: word.meaning)
        self.word = word.word
        self.explanation = explanation
        self.entry = entry

        if let entry {
            // Dictionary first. A base form ("edge") has no pill; a form ("edges") has one.
            if let form = entry.matchedForm {
                pillText = "\(form.type) of \(entry.word)"
            } else {
                pillText = nil
            }
            chipText = entry.senses.first?.chipText
        } else {
            pillText = baseFormNote
            chipText = nil
        }
    }

    /// Splits "took out (base form: draw out)" into "took out" and "base form: draw out".
    /// The note is nil when the meaning has none.
    static func splitBaseFormNote(from meaning: String) -> (explanation: String, note: String?) {
        guard let marker = meaning.range(of: "base form:", options: .caseInsensitive) else {
            return (meaning, nil)
        }
        let note = meaning[marker.lowerBound...]
            .trimmingCharacters(in: CharacterSet(charactersIn: " ()."))
        let explanation = meaning[..<marker.lowerBound]
            .trimmingCharacters(in: CharacterSet(charactersIn: " (;,"))
        // Keep the whole text if the note was the only thing in it.
        return (explanation.isEmpty ? meaning : explanation, note)
    }
}
