//
//  CacheKeys.swift
//  EnglishTranslation
//

import CryptoKit
import Foundation

/// The prompt versions of the backend. They are part of every cache key: when the backend bumps one,
/// old answers get a new key and are not served again.
/// Keep these equal to `app.claude.sentence-prompt-version` and `app.claude.word-prompt-version`
/// in `backend/src/main/resources/application.properties`.
enum PromptVersions {
    static let sentence = "v4"
    static let word = "v6"
}

/// Builds the same cache keys as `CacheKeys.java` in the backend, so one key means one answer
/// on the phone and on the server. Change both files together.
enum CacheKeys {
    /// The kind of answer a key points to.
    enum Kind: String {
        case sentence
        case word
    }

    /// Trims and collapses inner whitespace. Case is kept: "US" and "us" are different.
    static func normalizeSentence(_ text: String) -> String {
        // Java's strip() removes Character.isWhitespace characters (not the no-break space).
        // Java's \s+ matches only the ASCII ones.
        let scalars = Array(text.unicodeScalars)
        guard
            let start = scalars.firstIndex(where: { !isJavaWhitespace($0) }),
            let end = scalars.lastIndex(where: { !isJavaWhitespace($0) })
        else { return "" }

        var result = String.UnicodeScalarView()
        var previousWasSpace = false
        for scalar in scalars[start ... end] {
            if isAsciiSpace(scalar) {
                if !previousWasSpace { result.append(" ") }
                previousWasSpace = true
            } else {
                result.append(scalar)
                previousWasSpace = false
            }
        }
        return String(result)
    }

    /// Same as a sentence, plus lowercase: "Run" and "run" are the same word.
    static func normalizeWord(_ word: String) -> String {
        normalizeSentence(word).lowercased()
    }

    /// True when the input is one token after normalising: a Word in the spec (R1). Tokens are separated
    /// by the spaces that `normalizeSentence` leaves, so "Run", " run " and "don't" are one token and "go home" is two.
    /// An empty input has no token.
    static func isSingleToken(_ text: String) -> Bool {
        let normalized = normalizeSentence(text)
        return !normalized.isEmpty && !normalized.contains(" ")
    }

    /// For the word path only: normalises like `normalizeSentence`, then removes punctuation and symbols
    /// at the start and the end. "Run!", "(run)" and "“run”" give "Run"; "don't" and "well-known" keep their
    /// inner marks. Returns "" if nothing is left ("?!"). Case is kept: `normalizeWord` lowercases later.
    static func trimmingWordPunctuation(_ text: String) -> String {
        let marks = CharacterSet.punctuationCharacters.union(.symbols)
        let scalars = Array(normalizeSentence(text).unicodeScalars)
        guard
            let start = scalars.firstIndex(where: { !marks.contains($0) }),
            let end = scalars.lastIndex(where: { !marks.contains($0) })
        else { return "" }
        return String(String.UnicodeScalarView(scalars[start ... end]))
    }

    /// The key for a sentence answer: sha256 of `sentence|promptVersion|targetLang|text`.
    static func sentenceKey(
        _ text: String,
        targetLang: String,
        promptVersion: String = PromptVersions.sentence
    ) -> String {
        key(Kind.sentence.rawValue, promptVersion, targetLang, normalizeSentence(text))
    }

    /// The key for a word answer: sha256 of `word|promptVersion|word`. A word has no language.
    static func wordKey(_ word: String, promptVersion: String = PromptVersions.word) -> String {
        key(Kind.word.rawValue, promptVersion, normalizeWord(word))
    }

    /// sha256 of the parts joined with "|", as 64 lowercase hex characters.
    static func key(_ parts: String...) -> String {
        let digest = SHA256.hash(data: Data(parts.joined(separator: "|").utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private static func isAsciiSpace(_ scalar: Unicode.Scalar) -> Bool {
        // Java's \s: space, \t, \n, \u000B, \f, \r
        scalar == " " || (0x09 ... 0x0D).contains(scalar.value)
    }

    private static func isJavaWhitespace(_ scalar: Unicode.Scalar) -> Bool {
        if isAsciiSpace(scalar) || (0x1C ... 0x1F).contains(scalar.value) { return true }
        switch scalar.value {
        case 0x1680, 0x2000 ... 0x2006, 0x2008 ... 0x200A, 0x2028, 0x2029, 0x205F, 0x3000: return true
        default: return false // includes the no-break spaces 00A0, 2007, 202F
        }
    }
}
