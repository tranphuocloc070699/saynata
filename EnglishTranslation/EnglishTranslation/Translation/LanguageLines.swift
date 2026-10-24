//
//  LanguageLines.swift
//  EnglishTranslation
//

import Foundation

/// Gets the target-language line of a word card from the backend (TASK-106.3, doc-5).
/// One call asks for every sense of the card, so opening the other senses needs no new request.
/// Lines are kept in `TranslationCache` per (text, language). A missing line or a failed call is not an error here:
/// the caller falls back to the dictionary text (Vietnamese) or Apple Translation (other languages).
@MainActor
final class LanguageLines {
    static let shared = LanguageLines(backend: { try? TranslatorFactory.makeLineTranslator() })

    private struct Key: Hashable {
        let text: String
        let language: TargetLanguage
    }

    private let backend: () -> (any LineTranslating)?
    private let cache: TranslationCache
    /// Texts that are being asked right now, so two rows that open together send one request.
    private var pending: [Key: Task<Void, Never>] = [:]

    init(backend: @escaping () -> (any LineTranslating)?, cache: TranslationCache = .shared) {
        self.backend = backend
        self.cache = cache
    }

    /// The saved line for `text`, if there is one.
    func line(for text: String, in language: TargetLanguage) -> String? {
        cache.translation(of: text, in: language)
    }

    /// Makes sure the lines of `texts` are in the cache when the backend has them. Returns when the answer is here.
    /// Texts that are already cached or already being asked are not sent again.
    func load(_ texts: [String], in language: TargetLanguage) async {
        var toAsk: [String] = []
        var waits: [Task<Void, Never>] = []
        for text in Self.unique(texts) where cache.translation(of: text, in: language) == nil {
            if let running = pending[Key(text: text, language: language)] {
                waits.append(running)
            } else {
                toAsk.append(text)
            }
        }

        if !toAsk.isEmpty {
            let task = Task { await ask(toAsk, in: language) }
            for text in toAsk { pending[Key(text: text, language: language)] = task }
            waits.append(task)
        }
        for task in waits { await task.value }
    }

    private func ask(_ texts: [String], in language: TargetLanguage) async {
        defer { for text in texts { pending[Key(text: text, language: language)] = nil } }
        guard let backend = backend(),
              let lines = try? await backend.lines(for: texts, in: language),
              lines.count == texts.count else { return }
        for (text, line) in zip(texts, lines) {
            if let line { cache.save(line, of: text, in: language) }
        }
    }

    /// Non-empty texts without repeats, in order.
    private static func unique(_ texts: [String]) -> [String] {
        var seen = Set<String>()
        return texts.filter { !$0.isEmpty && seen.insert($0).inserted }
    }
}

extension WordEntry {
    /// The simple explanation of every sense: the texts one card asks the backend to translate.
    var senseDefinitions: [String] { senses.map(\.simpleDefinition) }
}
