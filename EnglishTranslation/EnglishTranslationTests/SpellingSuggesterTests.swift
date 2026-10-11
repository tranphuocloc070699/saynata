//
//  SpellingSuggesterTests.swift
//  EnglishTranslationTests
//

import Testing
@testable import EnglishTranslation

/// Counts how many times the word list was loaded.
private final class LoadCounter: @unchecked Sendable {
    var count = 0
}

struct SpellingSuggesterTests {
    /// A small word list. Lower rank = more common.
    private func makeSuggester(counter: LoadCounter = LoadCounter()) -> SpellingSuggester {
        SpellingSuggester(loadCandidates: {
            counter.count += 1
            return [
                SpellingCandidate(spelling: "run", lemma: "run", rank: 100),
                SpellingCandidate(spelling: "ran", lemma: "run", rank: 100),
                SpellingCandidate(spelling: "runs", lemma: "run", rank: 100),
                SpellingCandidate(spelling: "rung", lemma: "rung", rank: 2000),
                SpellingCandidate(spelling: "apple", lemma: "apple", rank: 900),
                SpellingCandidate(spelling: "form", lemma: "form", rank: 300),
                SpellingCandidate(spelling: "from", lemma: "from", rank: 10),
            ]
        })
    }

    @Test func runjSuggestsRunFirst() {
        #expect(makeSuggester().suggestions(for: "runj") == ["run", "rung"])
    }

    @Test func missingLetterIsFound() {
        #expect(makeSuggester().suggestions(for: "aple") == ["apple"])
    }

    @Test func farTypoHasNoSuggestion() {
        #expect(makeSuggester().suggestions(for: "zzzzqq").isEmpty)
    }

    @Test func correctWordIsNotCorrected() {
        #expect(makeSuggester().suggestions(for: "from").isEmpty)
        #expect(makeSuggester().suggestions(for: "ran").isEmpty)  // a form counts as correct too
    }

    @Test func swappedNeighbourLettersCountAsOneMistake() {
        #expect(SpellingSuggester.distance("form", "from") == 1)
        #expect(SpellingSuggester.distance("run", "runj") == 1)
        #expect(SpellingSuggester.distance("kitten", "sitting") == 3)
    }

    @Test func shortWordsAllowOneMistakeLongWordsTwo() {
        #expect(SpellingSuggester.maxDistance(forLength: 4) == 1)
        #expect(SpellingSuggester.maxDistance(forLength: 5) == 2)
        // "rnuj" is 2 away from "run": too far for a 4-letter word.
        #expect(makeSuggester().suggestions(for: "rnuj").isEmpty)
        // "axxle" (5 letters) may be 2 away from "apple".
        #expect(makeSuggester().suggestions(for: "axxle") == ["apple"])
    }

    @Test func formsSuggestTheirBaseWordOnce() {
        // "rans" is 1 from "ran" and "runs"; both are "run", which must show once.
        #expect(makeSuggester().suggestions(for: "rans") == ["run"])
    }

    @Test func atMostThreeAreReturned() {
        let suggester = SpellingSuggester(loadCandidates: {
            ["cat", "cut", "cot", "cap", "can"].enumerated().map {
                SpellingCandidate(spelling: $1, lemma: $1, rank: $0)
            }
        })
        #expect(suggester.suggestions(for: "cax") == ["cat", "cap", "can"])
    }

    @Test func wordListIsLoadedOnlyOnce() {
        let counter = LoadCounter()
        let suggester = makeSuggester(counter: counter)
        #expect(counter.count == 0)  // lazy: nothing loaded yet
        _ = suggester.suggestions(for: "runj")
        _ = suggester.suggestions(for: "aple")
        #expect(counter.count == 1)
    }

    @Test func bundledDictionarySuggestsRunForRunj() throws {
        let dictionary = try DictionaryStore.bundled()
        let suggester = SpellingSuggester(loadCandidates: { (try? dictionary.spellingCandidates()) ?? [] })
        #expect(suggester.suggestions(for: "runj").first == "run")
        #expect(suggester.suggestions(for: "zzzzqq").isEmpty)
        #expect(suggester.suggestions(for: "run").isEmpty)
    }
}
