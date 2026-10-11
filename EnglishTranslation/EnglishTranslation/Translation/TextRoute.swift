//
//  TextRoute.swift
//  EnglishTranslation
//

import Foundation

/// Where a piece of text goes. Used by the share extension to decide what to show
/// before it calls the backend. The app itself uses `TranslationStore`, which has the same word/sentence split.
enum TextRoute: Equatable {
    /// The backend accepts 1 to 500 characters of text (`TranslationRequest.java`, `@Size(max = 500)`).
    static let maxLength = 500

    /// Nothing but spaces (or nothing at all).
    case empty
    /// Longer than `maxLength`. Holds the length, to show in the message.
    case tooLong(length: Int)
    /// One token: the word path (bundle, then phone cache, then backend).
    case word
    /// More than one token: the sentence explanation.
    case sentence

    init(text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        // Java counts UTF-16 units, not characters, so count the same way as the server.
        let length = trimmed.utf16.count
        if trimmed.isEmpty {
            self = .empty
        } else if length > Self.maxLength {
            self = .tooLong(length: length)
        } else if CacheKeys.isSingleToken(trimmed) {
            self = .word
        } else {
            self = .sentence
        }
    }

    /// The message for a text the app cannot explain. Nil when the text can be explained.
    var problemMessage: String? {
        switch self {
        case .empty:
            "No text found. Select some words, then share them."
        case let .tooLong(length):
            "This text has \(length) characters. The limit is \(Self.maxLength). Select a shorter part."
        case .word, .sentence:
            nil
        }
    }
}
