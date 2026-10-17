//
//  WordEntry.swift
//  EnglishTranslation
//

import CoreGraphics
import Foundation

/// A dictionary entry for one word: how to say it, and its meanings.
struct WordEntry: Codable, Equatable, Sendable {
    /// The base form, for example "run" (even if the user typed "ran").
    let word: String
    /// How to say it, for example "/ɹʌn/". Some words have none.
    let ipa: String?
    let senses: [Sense]
    /// Set when the user typed a form of the word ("ran" for "run"). Nil when they typed the base form.
    let matchedForm: MatchedForm?

    init(word: String, ipa: String?, senses: [Sense], matchedForm: MatchedForm? = nil) {
        self.word = word
        self.ipa = ipa
        self.senses = senses
        self.matchedForm = matchedForm
    }
}

/// The form the user typed, for example "ran" (a "past tense" of "run").
struct MatchedForm: Codable, Equatable, Sendable {
    /// The form as typed, lowercased: "ran".
    let text: String
    /// What kind of form it is, in plain words: "past tense", "plural", "past participle".
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
    let examples: [Example]
    /// Common ways to use the word. Empty for now; TASK-24 fills it.
    let usage: [Usage]
    /// Optional relative path or URL to an image or action clip.
    let imageUrl: String?
    /// The picture's size in pixels, when known, so the frame has the right shape before the file loads.
    let imageWidth: Int?
    let imageHeight: Int?

    private enum CodingKeys: String, CodingKey {
        case pos, cefr, simpleDefinition, vietnamese, examples, usage
        case imageUrl, imageWidth, imageHeight
        case imageURL = "image_url"
    }

    init(pos: String, cefr: String, simpleDefinition: String, vietnamese: String,
         examples: [Example], usage: [Usage] = [], imageUrl: String? = nil,
         imageWidth: Int? = nil, imageHeight: Int? = nil) {
        self.pos = pos
        self.cefr = cefr
        self.simpleDefinition = simpleDefinition
        self.vietnamese = vietnamese
        self.examples = examples
        self.usage = usage
        self.imageUrl = imageUrl
        self.imageWidth = imageWidth
        self.imageHeight = imageHeight
    }

    /// The picture's size, or nil when it is not known (the view then waits for the file).
    var imagePixelSize: CGSize? {
        guard let imageWidth, let imageHeight, imageWidth > 0, imageHeight > 0 else { return nil }
        return CGSize(width: imageWidth, height: imageHeight)
    }

    // `usage` may be missing in older JSON, so it falls back to an empty list.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        pos = try container.decode(String.self, forKey: .pos)
        cefr = try container.decode(String.self, forKey: .cefr)
        simpleDefinition = try container.decode(String.self, forKey: .simpleDefinition)
        vietnamese = try container.decode(String.self, forKey: .vietnamese)
        examples = try container.decode([Example].self, forKey: .examples)
        usage = try container.decodeIfPresent([Usage].self, forKey: .usage) ?? []
        imageUrl = try container.decodeIfPresent(String.self, forKey: .imageUrl)
            ?? container.decodeIfPresent(String.self, forKey: .imageURL)
        imageWidth = try container.decodeIfPresent(Int.self, forKey: .imageWidth)
        imageHeight = try container.decodeIfPresent(Int.self, forKey: .imageHeight)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(pos, forKey: .pos)
        try container.encode(cefr, forKey: .cefr)
        try container.encode(simpleDefinition, forKey: .simpleDefinition)
        try container.encode(vietnamese, forKey: .vietnamese)
        try container.encode(examples, forKey: .examples)
        try container.encode(usage, forKey: .usage)
        try container.encodeIfPresent(imageUrl, forKey: .imageUrl)
        try container.encodeIfPresent(imageWidth, forKey: .imageWidth)
        try container.encodeIfPresent(imageHeight, forKey: .imageHeight)
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

/// One example sentence. `level` says how hard the sentence is; old data has none.
struct Example: Codable, Equatable, Sendable, ExpressibleByStringLiteral {
    enum Level: String, Codable, Sendable {
        case easy, medium, harder
    }

    let level: Level?
    let text: String

    init(level: Level? = nil, text: String) {
        self.level = level
        self.text = text
    }

    /// Lets `examples: ["It rains."]` work in tests and samples.
    init(stringLiteral value: String) {
        self.init(text: value)
    }

    // Old data is a plain string: "It rains." New data is { "level": "easy", "text": "It rains." }.
    init(from decoder: Decoder) throws {
        if let plain = try? decoder.singleValueContainer().decode(String.self) {
            level = nil
            text = plain
            return
        }
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let raw = try container.decodeIfPresent(String.self, forKey: .level)
        level = raw.flatMap(Level.init(rawValue:))
        text = try container.decode(String.self, forKey: .text)
    }
}
