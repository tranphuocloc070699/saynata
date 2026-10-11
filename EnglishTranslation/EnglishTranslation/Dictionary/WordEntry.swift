//
//  WordEntry.swift
//  EnglishTranslation
//

import Foundation

/// A dictionary entry for one word: how to say it, and its meanings.
struct WordEntry: Codable, Equatable, Sendable {
    /// The base form, for example "run" (even if the user typed "ran").
    let word: String
    /// How to say it, for example "/ɹʌn/". Some words have none.
    let ipa: String?
    let senses: [Sense]
    /// Set when the user typed a form of the word, for example "ran" gives form "ran", type "past tense".
    /// Nil when they typed the base form, or when the word came from somewhere else than the dictionary file.
    var matchedForm: WordForm? = nil
}

/// A form of a word, such as "ran", and what it is: "past tense", "past participle", "-ing form",
/// "third person", "plural", "comparative", "superlative", or "form" when the pipeline is not sure.
struct WordForm: Codable, Equatable, Sendable {
    let form: String
    let type: String
}

/// One meaning of a word.
struct Sense: Codable, Equatable, Sendable {
    /// Part of speech: "verb", "noun", ...
    let pos: String
    /// Level from A1 to C2.
    let cefr: String
    /// A short English explanation in plain words.
    let simpleDefinition: String
    /// The Vietnamese meaning. Show it last (see CLAUDE.md).
    let vietnamese: String
    /// Example sentences, easy first. Show `example.text`.
    let examples: [Example]
    /// Common ways to use the word. Empty for now; TASK-24 fills it.
    let usage: [Usage]

    init(pos: String, cefr: String, simpleDefinition: String, vietnamese: String,
         examples: [Example], usage: [Usage] = []) {
        self.pos = pos
        self.cefr = cefr
        self.simpleDefinition = simpleDefinition
        self.vietnamese = vietnamese
        self.examples = examples
        self.usage = usage
    }

    // `usage` may be missing in older JSON, so it falls back to an empty list.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        pos = try container.decode(String.self, forKey: .pos)
        cefr = try container.decode(String.self, forKey: .cefr)
        simpleDefinition = try container.decode(String.self, forKey: .simpleDefinition)
        vietnamese = try container.decode(String.self, forKey: .vietnamese)
        // Old JSON has plain strings. They become easy, medium (see `Example.fromPlainStrings`).
        if let levelled = try? container.decode([Example].self, forKey: .examples) {
            examples = levelled
        } else {
            examples = Example.fromPlainStrings(try container.decode([String].self, forKey: .examples))
        }
        usage = try container.decodeIfPresent([Usage].self, forKey: .usage) ?? []
    }
}

/// One example sentence and how hard it is.
struct Example: Codable, Equatable, Sendable, ExpressibleByStringLiteral {
    enum Level: String, Codable, Sendable {
        case easy, medium, harder
    }

    let level: Level
    let text: String

    init(level: Level = .easy, text: String) {
        self.level = level
        self.text = text
    }

    /// Lets code write `examples: ["I run."]`. The level is easy.
    init(stringLiteral value: String) {
        self.init(text: value)
    }

    // A missing or unknown `level` falls back to `.easy`.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let raw = try container.decodeIfPresent(String.self, forKey: .level)
        level = raw.flatMap(Level.init(rawValue:)) ?? .easy
        text = try container.decode(String.self, forKey: .text)
    }

    /// Old data stored two plain sentences per sense, in no order. They become easy, then medium.
    static func fromPlainStrings(_ texts: [String]) -> [Example] {
        texts.enumerated().map { index, text in
            Example(level: index == 0 ? .easy : .medium, text: text)
        }
    }

    /// Reads the examples JSON from the dictionary file: `[{"level": "easy", "text": "..."}]`,
    /// or the old `["...", "..."]`. A missing value or broken JSON gives an empty list.
    static func decodeList(_ json: String?) -> [Example] {
        guard let data = json?.data(using: .utf8) else { return [] }
        if let levelled = try? JSONDecoder().decode([Example].self, from: data) { return levelled }
        return fromPlainStrings((try? JSONDecoder().decode([String].self, from: data)) ?? [])
    }
}

/// A usage pattern, like "run out of" with an example sentence.
struct Usage: Codable, Equatable, Sendable {
    /// What kind of usage this is.
    enum Kind: String, Codable, Sendable {
        case pattern, collocation, phrase, idiom
    }

    let kind: Kind
    let pattern: String
    let example: String

    init(kind: Kind = .pattern, pattern: String, example: String) {
        self.kind = kind
        self.pattern = pattern
        self.example = example
    }

    // A missing or unknown `kind` falls back to `.pattern`.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let raw = try container.decodeIfPresent(String.self, forKey: .kind)
        kind = raw.flatMap(Kind.init(rawValue:)) ?? .pattern
        pattern = try container.decode(String.self, forKey: .pattern)
        example = try container.decode(String.self, forKey: .example)
    }
}
