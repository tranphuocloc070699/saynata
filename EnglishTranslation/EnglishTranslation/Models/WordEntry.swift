//
//  WordEntry.swift
//  EnglishTranslation
//

import Foundation

/// A dictionary entry for one word: how to say it, and one or more senses (meanings).
/// Shared by the dictionary (TASK-4), the backend lookup (TASK-6) and the word card.
struct WordEntry: Hashable, Identifiable {
    let word: String
    /// How to say it, for example "/dɪˈpɛnd/". Missing for some words.
    var ipa: String?
    var senses: [Sense]

    var id: String { word }
}

/// One meaning of a word.
struct Sense: Hashable {
    /// Part of speech: "verb", "noun", "adj"...
    var pos: String
    /// CEFR level: "A1" to "C2". Missing for some words.
    var cefr: String?
    /// The meaning in short, plain English. This is the hero of the card.
    var simpleDefinition: String
    /// The Vietnamese helper line. Shown last, collapsed by default.
    var vietnamese: String?
    var examples: [String] = []
    var usage: [Usage] = []

    /// Text for the level chip: "verb · A2", or just "verb" when the level is missing.
    var chipText: String {
        guard let cefr else { return pos }
        return "\(pos) · \(cefr)"
    }
}

/// One "How to use it" item: a pattern and an example that follows it.
/// Placeholders in `pattern` read "someone", "something", "+ noun", "+ verb", "+ -ing".
struct Usage: Hashable {
    /// What kind of item this is. Decides the small label shown before the pattern.
    enum Kind: String, Hashable {
        case pattern
        case collocation
        case phrase
        case idiom

        /// Small label above the pattern. A plain pattern has no label.
        var label: String? {
            self == .pattern ? nil : rawValue
        }
    }

    var kind: Kind = .pattern
    /// For example "depend on + noun".
    var pattern: String
    /// For example "It depends on the weather."
    var example: String
}
