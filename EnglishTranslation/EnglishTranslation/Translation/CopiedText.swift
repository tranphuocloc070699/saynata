//
//  CopiedText.swift
//  EnglishTranslation
//

import Foundation

/// Cleans text that came from the clipboard before it is explained (the Mac shortcut, TASK-85).
///
/// Apple Books adds a citation when you copy from a book, and puts the passage in curly quotes:
///
///     “It was a bright cold day in April.”
///
///     Excerpt From
///     Nineteen Eighty-Four
///     George Orwell
///     This material may be protected by copyright.
///
/// Only the passage is kept: "It was a bright cold day in April."
enum CopiedText {
    static func cleaned(_ text: String) -> String {
        var result = text.trimmingCharacters(in: .whitespacesAndNewlines)

        // A last paragraph that talks about copyright is a citation (Books, and some other readers): drop everything
        // after the first paragraph.
        let paragraphs = result.components(separatedBy: "\n\n")
        if paragraphs.count > 1, let last = paragraphs.last, last.localizedCaseInsensitiveContains("copyright") {
            result = paragraphs[0].trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // The passage in one pair of quotes: remove them.
        let pairs: [(Character, Character)] = [("\u{201C}", "\u{201D}"), ("\"", "\"")]
        for (open, close) in pairs where result.count > 2 && result.first == open && result.last == close {
            let inner = result.dropFirst().dropLast()
            // Only when there is no other quote inside: "A" and "B" must keep its quotes.
            if !inner.contains(open) && !inner.contains(close) {
                result = String(inner).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return result
    }
}
