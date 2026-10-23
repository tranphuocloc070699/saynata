//
//  SummaryMarkup.swift
//  EnglishTranslation
//

import Foundation

/// The small Markdown of a sentence summary (prompt v9, TASK-89.3): plain lines, bullet lines that start with
/// "- ", and **bold** key words. Nothing else is read.
///
/// `text` is what the card shows: the markers are gone and a bullet line starts with "•" and a tab. The ranges
/// point into `text` (UTF-16, like `NSAttributedString`), so tapping a word still finds the word the user sees.
/// While the answer streams, a "**" that is not closed yet makes the rest of the line bold, and the marker
/// never shows.
struct SummaryMarkup: Equatable {
    /// The bullet at the start of a bullet line in `text`.
    static let bulletPrefix = "\u{2022}\t"

    let text: String
    let boldRanges: [NSRange]
    /// Each bullet line in `text`, from its "•" to the end of the line (no newline).
    let bulletRanges: [NSRange]

    /// Reads `markdown`. Text that is not a summary (the sentence the user typed) uses `init(plain:)`.
    init(_ markdown: String) {
        var output = ""
        var bold: [NSRange] = []
        var bullets: [NSRange] = []
        let lines = markdown.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n")

        for (index, rawLine) in lines.enumerated() {
            var line = Substring(rawLine)
            let lineStart = (output as NSString).length
            if line.hasPrefix("- ") {
                line = line.dropFirst(2)
                output += Self.bulletPrefix
            }
            // Text and "**" alternate: every odd piece is bold. The last odd piece of a streaming line is open.
            let pieces = line.components(separatedBy: "**")
            for (pieceIndex, piece) in pieces.enumerated() {
                let start = (output as NSString).length
                output += piece
                let length = (output as NSString).length - start
                if pieceIndex % 2 == 1, length > 0 {
                    bold.append(NSRange(location: start, length: length))
                }
            }
            if rawLine.hasPrefix("- ") {
                bullets.append(NSRange(location: lineStart, length: (output as NSString).length - lineStart))
            }
            if index < lines.count - 1 {
                output += "\n"
            }
        }
        text = output
        boldRanges = bold
        bulletRanges = bullets
    }

    /// Text shown as it is, with no Markdown read.
    init(plain: String) {
        text = plain
        boldRanges = []
        bulletRanges = []
    }

    /// The words only, one line per idea: for Listen, Copy, Liked and History.
    var plainText: String {
        text.replacingOccurrences(of: Self.bulletPrefix, with: "")
    }

    /// The summary for SwiftUI `Text` (Mac card, quick explain snippet): bold kept, bullets as "•".
    var attributedString: AttributedString {
        // The tab becomes a space: the same length, so the ranges still fit.
        let shown = NSMutableAttributedString(string: text.replacingOccurrences(of: Self.bulletPrefix, with: "\u{2022} "))
        for range in boldRanges where NSMaxRange(range) <= shown.length {
            shown.addAttribute(
                .inlinePresentationIntent,
                value: InlinePresentationIntent.stronglyEmphasized.rawValue,
                range: range)
        }
        return (try? AttributedString(shown, including: \.foundation)) ?? AttributedString(plainText)
    }
}
