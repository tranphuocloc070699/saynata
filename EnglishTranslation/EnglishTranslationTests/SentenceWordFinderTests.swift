//
//  SentenceWordFinderTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// The extra words in a sentence result (TASK-56).
struct SentenceWordFinderTests {
    private let senses = [Sense(pos: "noun", cefr: "A1", simpleDefinition: "a thing", vietnamese: "x", examples: [])]

    /// A small fake dictionary: spelling -> base word and form type (nil type = the base form itself).
    private func fakeDictionary() -> (String) -> WordEntry? {
        let table: [String: (lemma: String, type: String?)] = [
            "like": ("like", nil), "apples": ("apple", "plural"), "apple": ("apple", nil),
            "ran": ("run", "past tense"), "run": ("run", nil),
            "shops": ("shop", "plural"), "shop": ("shop", nil),
            "is": ("be", "third person"), "have": ("have", nil), "she": ("she", nil),
            "the": ("the", nil), "to": ("to", nil),
            "blurred": ("blur", "past tense"), "edges": ("edge", "plural"),
        ]
        return { word in
            guard let hit = table[word.lowercased()] else { return nil }
            let form = hit.type.map { MatchedForm(text: word.lowercased(), type: $0) }
            return WordEntry(word: hit.lemma, ipa: nil, senses: senses, matchedForm: form)
        }
    }

    private func words(_ sentence: String, claude: [Explanation.Word] = []) -> [String] {
        SentenceResult(
            sentence: sentence,
            explanation: Explanation(meaning: "m", words: claude, translation: "t"),
            language: .vietnamese,
            bundledEntry: fakeDictionary()
        ).words.map(\.word)
    }

    @Test func iLikeApplesGivesLikeAndApple() {
        #expect(words("I like apples") == ["like", "apples"])
    }

    @Test func formsMatchTheBaseWordAndFunctionWordsAreSkipped() {
        let result = words("She ran to the shops")
        #expect(result == ["ran", "shops"])
    }

    @Test func extraWordCardShowsThePillAndTheDictionaryMeaning() {
        let result = SentenceResult(
            sentence: "She ran to the shops",
            explanation: Explanation(meaning: "m", words: [], translation: "t"),
            language: .vietnamese,
            bundledEntry: fakeDictionary())
        #expect(result.words[0].pillText == "past tense of run")
        #expect(result.words[0].explanation == "a thing")
        #expect(result.words[0].chipText == "noun · A1")
    }

    @Test func helperVerbsAreSkipped() {
        #expect(words("The apple is red and I have a shop") == ["apple", "shop"])
    }

    @Test func claudeWordsComeFirstAndAreNotRepeated() {
        let claude = [Explanation.Word(word: "shops", partOfSpeech: "noun", meaning: "places that sell things")]
        let result = SentenceResult(
            sentence: "She ran to the shops",
            explanation: Explanation(meaning: "m", words: claude, translation: "t"),
            language: .vietnamese,
            bundledEntry: fakeDictionary())
        #expect(result.words.map(\.word) == ["shops", "ran"])
        #expect(result.words[0].explanation == "places that sell things")
    }

    @Test func aBaseWordClaudeListedBlocksItsForms() {
        // Claude listed "shops"; the base word "shop" in the same sentence is the same word.
        let claude = [Explanation.Word(word: "shops", partOfSpeech: "noun", meaning: "places")]
        let result = words("A shop and shops", claude: claude)
        #expect(result == ["shops"])
    }

    @Test func sameWordTwiceIsShownOnce() {
        #expect(words("apple apples apple") == ["apple"])
    }

    @Test func wordsNotInTheDictionaryAreSkipped() {
        #expect(words("I like zorbs") == ["like"])
    }

    @Test func numbersAreSkipped() {
        #expect(words("I like 3 apples") == ["like", "apples"])
    }
}
