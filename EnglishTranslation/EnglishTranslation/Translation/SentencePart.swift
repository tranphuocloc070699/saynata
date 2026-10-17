//
//  SentencePart.swift
//  EnglishTranslation
//

import Foundation

/// A part of a sentence that the user selected (TASK-89.2), turned into what the popup needs.
enum SentencePart: Equatable {
    /// The selection is exactly one word: the app opens the word card, not a popup.
    case word(SentenceToken)
    /// Two or more words (or part of a word and more): the popup explains this text.
    case phrase(String)

    /// The shortest selection that counts: two characters, as the developer asked.
    static let minimumLength = 2

    /// - Parameters:
    ///   - range: The selection, in UTF-16 units of `text`.
    ///   - text: The whole sentence the selection is in.
    /// - Returns: Nil when the selection is empty, shorter than two characters (after trimming), or has no letter.
    static func from(range: NSRange, in text: String) -> SentencePart? {
        let string = text as NSString
        guard range.location != NSNotFound, range.length > 0, NSMaxRange(range) <= string.length else { return nil }

        // Selection handles often catch a space or a comma at the edge. Those do not count.
        let edge = CharacterSet.whitespacesAndNewlines.union(.punctuationCharacters).union(.symbols)
        var start = range.location
        var end = NSMaxRange(range)
        while start < end, isEdge(string.character(at: start), edge) { start += 1 }
        while end > start, isEdge(string.character(at: end - 1), edge) { end -= 1 }

        let trimmed = NSRange(location: start, length: end - start)
        guard trimmed.length >= minimumLength else { return nil }
        let selected = string.substring(with: trimmed)
        guard selected.unicodeScalars.contains(where: { CharacterSet.letters.contains($0) }) else { return nil }

        let tokens = SentenceTokenizer.tokens(in: text)
        if let only = tokens.first(where: { NSEqualRanges($0.range, trimmed) }) {
            return .word(only)
        }
        // The same text the sentence endpoint accepts: spaces and line breaks collapsed.
        return .phrase(CacheKeys.normalizeSentence(selected))
    }

    private static func isEdge(_ unit: unichar, _ edge: CharacterSet) -> Bool {
        guard let scalar = Unicode.Scalar(unit) else { return false }
        return edge.contains(scalar)
    }
}

/// Waits until the selection stops changing, then reports the last one (TASK-89.2). While the user drags a
/// handle, the selection changes many times a second; only the settled one opens the popup.
/// Plain Swift: the clock is `Task.sleep`, so a test can use a very short delay.
@MainActor
final class SelectionDebouncer {
    private let delay: Duration
    private var task: Task<Void, Never>?

    /// - Parameter delay: How long the selection must stay the same. The app uses 0.4 seconds.
    init(delay: Duration = .milliseconds(400)) {
        self.delay = delay
    }

    /// Call on every change. Only the last call within `delay` runs `action`.
    func submit(_ action: @escaping @MainActor () -> Void) {
        task?.cancel()
        task = Task { [delay] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            action()
        }
    }

    /// Drops a waiting change, for example when the selection was cleared.
    func cancel() {
        task?.cancel()
        task = nil
    }
}
