//
//  SentenceTokenizer.swift
//  EnglishTranslation
//

import Foundation

/// One word of a sentence, with where it sits in the text (TASK-89.2).
struct SentenceToken: Equatable {
    /// The word as it is written, for example "Blurred" or "don't".
    let text: String
    /// Where the word is. `NSRange` counts UTF-16 units, the same as `UITextView` and `NSAttributedString`.
    let range: NSRange

    /// The word to look up: lowercase, with a closing "'s" removed ("world's" gives "world").
    /// Forms like "blurred" are left as they are: the dictionary lookup resolves them.
    var lookupWord: String {
        var word = text.replacingOccurrences(of: "\u{2019}", with: "'").lowercased()
        if word.hasSuffix("'s"), word.count > 2 { word.removeLast(2) }
        return word
    }
}

/// Cuts a sentence into words, so every word can be tapped. Plain Swift, no UIKit.
enum SentenceTokenizer {
    /// The words of `text`, in order. A word is letters and digits, and may contain an apostrophe or a hyphen
    /// between two letters or digits ("don't", "well-known"). Punctuation and spaces are not part of any word.
    /// A group with no letter ("2024") is not a word: it cannot be looked up.
    static func tokens(in text: String) -> [SentenceToken] {
        let string = text as NSString
        let length = string.length
        var tokens: [SentenceToken] = []
        var index = 0

        while index < length {
            guard isWordCharacter(string.character(at: index)) else {
                index += 1
                continue
            }
            let start = index
            index += 1
            while index < length {
                let unit = string.character(at: index)
                if isWordCharacter(unit) {
                    index += 1
                } else if isJoiner(unit), index + 1 < length, isWordCharacter(string.character(at: index + 1)) {
                    index += 2
                } else {
                    break
                }
            }
            let range = NSRange(location: start, length: index - start)
            let word = string.substring(with: range)
            if word.unicodeScalars.contains(where: { CharacterSet.letters.contains($0) }) {
                tokens.append(SentenceToken(text: word, range: range))
            }
        }
        return tokens
    }

    /// The word that has the character at `offset` (a UTF-16 offset), or nil when it is a space or a mark.
    static func token(at offset: Int, in tokens: [SentenceToken]) -> SentenceToken? {
        tokens.first { NSLocationInRange(offset, $0.range) }
    }

    private static func isWordCharacter(_ unit: unichar) -> Bool {
        // A UTF-16 surrogate half (an emoji, a rare letter) is never a word character here.
        guard let scalar = Unicode.Scalar(unit) else { return false }
        return CharacterSet.letters.contains(scalar)
            || CharacterSet.decimalDigits.contains(scalar)
            || CharacterSet.nonBaseCharacters.contains(scalar)
    }

    /// Apostrophes (straight and curly) and hyphens.
    private static func isJoiner(_ unit: unichar) -> Bool {
        unit == 0x27 || unit == 0x2019 || unit == 0x2D || unit == 0x2011
    }
}
