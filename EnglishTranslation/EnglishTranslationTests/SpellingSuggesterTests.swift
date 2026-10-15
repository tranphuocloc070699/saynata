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

// MARK: - Apple's checker and long words

/// A fake spell checker: returns the guesses it was given.
private struct FakeChecker: SpellChecking {
    var answers: [String: [String]] = [:]
    func guesses(for word: String) -> [String] { answers[word] ?? [] }
}

struct SpellingSuggesterCheckerTests {
    private let words: [SpellingCandidate] = [
        SpellingCandidate(spelling: "run", lemma: "run", rank: 100),
        SpellingCandidate(spelling: "rung", lemma: "rung", rank: 2000),
        SpellingCandidate(spelling: "receive", lemma: "receive", rank: 500),
        SpellingCandidate(spelling: "received", lemma: "receive", rank: 500),
        SpellingCandidate(spelling: "conversation", lemma: "conversation", rank: 800),
        SpellingCandidate(spelling: "convention", lemma: "convention", rank: 3000),
        SpellingCandidate(spelling: "control", lemma: "control", rank: 400),
    ]

    private func makeSuggester(_ answers: [String: [String]] = [:]) -> SpellingSuggester {
        SpellingSuggester(loadCandidates: { words }, checker: FakeChecker(answers: answers))
    }

    @Test func longWordFindsConversationWithoutAppleHelp() {
        // 15 letters: up to 5 mistakes. "conversation" is 5 away.
        #expect(SpellingSuggester.distance("controlvesation", "conversation") == 5)
        #expect(makeSuggester().suggestions(for: "controlvesation") == ["conversation"])
    }

    @Test func recieveSuggestsReceive() {
        #expect(makeSuggester(["recieve": ["receive"]]).suggestions(for: "recieve") == ["receive"])
    }

    @Test func runjStillSuggestsRunFirst() {
        // Apple has no answer: the bundled match "run" comes first, then "rung".
        #expect(makeSuggester().suggestions(for: "runj") == ["run", "rung"])
    }

    @Test func appleGuessesThatAreBundledComeBeforeCloseMatches() {
        let suggester = makeSuggester(["runj": ["rung"]])
        #expect(suggester.suggestions(for: "runj") == ["rung", "run"])
    }

    @Test func appleGuessesThatAreNotBundledComeLast() {
        let suggester = makeSuggester(["runj": ["runny", "run"]])
        #expect(suggester.suggestions(for: "runj") == ["run", "rung", "runny"])
    }

    @Test func aGuessThatIsAFormSuggestsItsBaseWord() {
        #expect(makeSuggester(["recieved": ["received"]]).suggestions(for: "recieved") == ["receive"])
    }

    @Test func correctBundledWordGivesNothingEvenWithAppleGuesses() {
        #expect(makeSuggester(["run": ["rung"]]).suggestions(for: "run").isEmpty)
    }

    @Test func resultHasNoDuplicatesAndAtMostThree() {
        let suggester = makeSuggester(["runj": ["run", "run", "rung", "runs", "runny", "runner", "rune"]])
        let result = suggester.suggestions(for: "runj")
        #expect(result.count == 3)
        #expect(Set(result).count == 3)
    }

    @Test func theTypedWordIsNeverSuggested() {
        let suggester = makeSuggester(["runj": ["runj", "RUNJ", "run"]])
        #expect(suggester.suggestions(for: "runj") == ["run", "rung"])
        #expect(suggester.suggestions(for: "RUNJ") == ["run", "rung"])
    }

    @Test func onlySingleLowercaseWordsFromApple() {
        let suggester = makeSuggester(["zzqx": ["Zack", "run now", "well-known", "don't", "fine"]])
        #expect(suggester.suggestions(for: "zzqx") == ["fine"])
    }

    @Test func maxDistanceGrowsWithLength() {
        #expect(SpellingSuggester.maxDistance(forLength: 4) == 1)
        #expect(SpellingSuggester.maxDistance(forLength: 5) == 2)
        #expect(SpellingSuggester.maxDistance(forLength: 8) == 2)
        #expect(SpellingSuggester.maxDistance(forLength: 9) == 3)
        #expect(SpellingSuggester.maxDistance(forLength: 15) == 5)
    }

    @Test func closeBundledWordsNeedTheSameFirstLetter() {
        // "xonversation" is 1 away from "conversation", but starts with another letter.
        #expect(makeSuggester().suggestions(for: "xonversation").isEmpty)
    }

    @Test func bundledDictionarySuggestsConversationForControlvesation() throws {
        let dictionary = try DictionaryStore.bundled()
        let suggester = SpellingSuggester(loadCandidates: { (try? dictionary.spellingCandidates()) ?? [] })
        #expect(suggester.suggestions(for: "controlvesation").contains("conversation"))
        #expect(suggester.suggestions(for: "controlvesation").count <= 3)
    }
}
