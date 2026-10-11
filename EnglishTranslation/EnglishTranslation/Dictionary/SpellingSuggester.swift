//
//  SpellingSuggester.swift
//  EnglishTranslation
//

import Foundation

/// One spelling the dictionary knows: a base word or a form ("ran"), the base word to suggest, and its NGSL rank.
struct SpellingCandidate: Equatable {
    let spelling: String
    let lemma: String
    /// NGSL rank. Lower is more common.
    let rank: Int
}

/// Finds close bundled words for a misspelled word ("runj" -> "run"). Works offline.
///
/// The word list (about 10,000 short strings) is loaded the first time it is needed, then kept in memory.
final class SpellingSuggester: @unchecked Sendable {
    private let loadCandidates: () -> [SpellingCandidate]
    private var loadedCandidates: [SpellingCandidate]?
    /// Protects `loadedCandidates`: suggestions can be asked from any thread.
    private let lock = NSLock()

    init(loadCandidates: @escaping () -> [SpellingCandidate]) {
        self.loadCandidates = loadCandidates
    }

    /// Up to `limit` base words close to `word`: nearest first, then the more common word first.
    /// Returns nothing when `word` is already a bundled spelling (a correct word is not "corrected").
    func suggestions(for word: String, limit: Int = 3) -> [String] {
        let typed = word.lowercased()
        guard !typed.isEmpty else { return [] }
        let maxDistance = Self.maxDistance(forLength: typed.count)
        let candidates = allCandidates()

        if candidates.contains(where: { $0.spelling == typed }) { return [] }

        // Keep the best match for each base word: "runs" and "ran" are both "run".
        var bestPerLemma: [String: (distance: Int, rank: Int)] = [:]
        for candidate in candidates {
            // A word whose length differs by more than the limit cannot be close. Skips most of the list.
            guard abs(candidate.spelling.count - typed.count) <= maxDistance else { continue }
            let distance = Self.distance(typed, candidate.spelling)
            guard distance <= maxDistance else { continue }
            if let current = bestPerLemma[candidate.lemma], current.distance <= distance { continue }
            bestPerLemma[candidate.lemma] = (distance, candidate.rank)
        }

        let ranked = bestPerLemma.sorted { first, second in
            if first.value.distance != second.value.distance { return first.value.distance < second.value.distance }
            if first.value.rank != second.value.rank { return first.value.rank < second.value.rank }
            return first.key < second.key  // same rank: a stable order
        }
        return ranked.prefix(limit).map(\.key)
    }

    /// 1 mistake for short words (4 letters or fewer), 2 for longer ones.
    static func maxDistance(forLength length: Int) -> Int {
        length <= 4 ? 1 : 2
    }

    /// Damerau-Levenshtein distance (the "optimal string alignment" kind): the fewest inserts, deletes,
    /// replacements and swaps of two neighbour letters ("form" -> "from" is 1) to turn `a` into `b`.
    /// See https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance
    static func distance(_ a: String, _ b: String) -> Int {
        let first = Array(a)
        let second = Array(b)
        if first.isEmpty { return second.count }
        if second.isEmpty { return first.count }

        // table[i][j] = distance between the first i letters of `a` and the first j letters of `b`.
        var table = Array(repeating: Array(repeating: 0, count: second.count + 1), count: first.count + 1)
        for i in 0...first.count { table[i][0] = i }
        for j in 0...second.count { table[0][j] = j }

        for i in 1...first.count {
            for j in 1...second.count {
                let cost = first[i - 1] == second[j - 1] ? 0 : 1
                table[i][j] = min(
                    table[i - 1][j] + 1,         // delete a letter
                    table[i][j - 1] + 1,         // insert a letter
                    table[i - 1][j - 1] + cost)  // replace a letter (or keep it)
                let isSwap = i > 1 && j > 1
                    && first[i - 1] == second[j - 2] && first[i - 2] == second[j - 1]
                if isSwap {
                    table[i][j] = min(table[i][j], table[i - 2][j - 2] + 1)
                }
            }
        }
        return table[first.count][second.count]
    }

    private func allCandidates() -> [SpellingCandidate] {
        lock.lock()
        defer { lock.unlock() }
        if let loadedCandidates { return loadedCandidates }
        let candidates = loadCandidates()
        loadedCandidates = candidates
        return candidates
    }
}
