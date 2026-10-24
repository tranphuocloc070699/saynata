//
//  BetterDiff.swift
//  EnglishTranslation
//

import Foundation

/// Which words of the fixed text are new (TASK-110), so "Better way to say it" can show them in bold.
/// Words are compared without case, punctuation around them and curly apostrophes, like the backend's
/// `BetterCheck`: "I already do it." and "I've already done it." give "I've" and "done".
struct BetterDiff: Equatable {
    /// One piece of the fixed text, in order. Joined, the pieces are exactly the fixed text.
    struct Piece: Equatable {
        let text: String
        let isChanged: Bool
    }

    let pieces: [Piece]

    init(original: String, better: String) {
        let originalWords = Self.tokens(in: original).map(\.key).filter { !$0.isEmpty }
        let betterTokens = Self.tokens(in: better)
        let kept = Self.keptIndices(originalWords, betterTokens.map(\.key))
        pieces = betterTokens.enumerated().map { index, token in
            Piece(text: token.text, isChanged: !token.key.isEmpty && !kept.contains(index))
        }
    }

    /// The fixed text with the changed words in bold, for `Text`.
    var attributedString: AttributedString {
        pieces.reduce(into: AttributedString()) { result, piece in
            var part = AttributedString(piece.text)
            if piece.isChanged {
                part.inlinePresentationIntent = .stronglyEmphasized
            }
            result += part
        }
    }

    /// The text split into words, each with the spaces after it, and its comparison key.
    private static func tokens(in text: String) -> [(text: String, key: String)] {
        var result: [(text: String, key: String)] = []
        var current = ""
        for character in text {
            if character.isWhitespace {
                current.append(character)
            } else {
                if let last = current.last, last.isWhitespace {
                    result.append((current, key(for: current)))
                    current = ""
                }
                current.append(character)
            }
        }
        if !current.isEmpty {
            result.append((current, key(for: current)))
        }
        return result
    }

    private static func key(for word: String) -> String {
        let plain = word.lowercased().replacingOccurrences(of: "\u{2019}", with: "'")
            .replacingOccurrences(of: "\u{2018}", with: "'")
        return plain.trimmingCharacters(in: CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "'")).inverted)
    }

    /// The indices in `b` that are part of the longest list of words both have in the same order.
    private static func keptIndices(_ a: [String], _ b: [String]) -> Set<Int> {
        guard !a.isEmpty, !b.isEmpty else { return [] }
        var lengths = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in 1...a.count {
            for j in 1...b.count {
                lengths[i][j] = !b[j - 1].isEmpty && a[i - 1] == b[j - 1]
                    ? lengths[i - 1][j - 1] + 1
                    : max(lengths[i - 1][j], lengths[i][j - 1])
            }
        }
        var kept = Set<Int>()
        var i = a.count, j = b.count
        while i > 0, j > 0 {
            if !b[j - 1].isEmpty, a[i - 1] == b[j - 1] {
                kept.insert(j - 1)
                i -= 1
                j -= 1
            } else if lengths[i - 1][j] >= lengths[i][j - 1] {
                i -= 1
            } else {
                j -= 1
            }
        }
        return kept
    }
}
