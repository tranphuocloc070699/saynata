//
//  SavedItem.swift
//  EnglishTranslation
//

import Foundation

/// One word, phrase or sentence in History or in Liked (TASK-93). History keeps it on the phone;
/// Liked keeps it on the server (`/api/liked`) and a copy on the phone.
struct SavedItem: Equatable, Identifiable, Sendable, Codable {
    /// The two kinds the backend knows. A phrase like "give up" is a `word`.
    enum Kind: String, Codable, Sendable {
        case word
        case sentence
    }

    let kind: Kind
    /// What the user saw: the word as shown, or the sentence.
    let text: String
    /// The short simple-English meaning for the list (the backend keeps up to 300 characters).
    let preview: String
    /// A small label for a word row, like "verb · A2". Nil when not known (a phrase, or an item liked on another device).
    let chip: String?
    /// When it was looked up (History) or liked (Liked).
    let date: Date

    var id: String { Self.key(kind: kind, text: text) }

    /// True for a word item that has more than one word, like "give up".
    var isPhrase: Bool { kind == .word && text.contains(" ") }

    /// The same key rule as the backend: a word is lowercased and trimmed, a sentence only trimmed.
    static func key(kind: Kind, text: String) -> String {
        let normalized = switch kind {
        case .word: CacheKeys.normalizeWord(text)
        case .sentence: text.trimmingCharacters(in: .whitespacesAndNewlines) // the backend only trims
        }
        return "\(kind.rawValue):\(normalized)"
    }

    static let maxPreviewLength = 300

    init(kind: Kind, text: String, preview: String, chip: String? = nil, date: Date = .now) {
        self.kind = kind
        self.text = text
        self.preview = String(preview.prefix(Self.maxPreviewLength))
        self.chip = chip
        self.date = date
    }

    /// A word or phrase result. The first sense gives the label and the preview.
    init(entry: WordEntry, date: Date = .now) {
        let first = entry.senses.first
        let text = entry.matchedForm?.text ?? entry.word
        let chip = first.map { sense in
            [sense.pos, sense.cefr].filter { !$0.isEmpty }.joined(separator: " · ")
        }
        self.init(
            kind: .word, text: text, preview: first?.simpleDefinition ?? "",
            chip: text.contains(" ") ? nil : chip, date: date)
    }

    /// A sentence result: the sentence and its simple summary.
    init(result: SentenceResult, date: Date = .now) {
        self.init(kind: .sentence, text: result.sentence, preview: SummaryMarkup(result.explanation.meaning).plainText, date: date)
    }
}
