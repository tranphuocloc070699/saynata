//
//  SentenceWordInfoTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// The pill and chip on a word card in a sentence result (TASK-41).
struct SentenceWordInfoTests {
    private let blurSenses = WordEntry.depend.senses  // first sense: "verb · A2"

    private func blurredEntry() -> WordEntry {
        WordEntry(word: "blur", ipa: "/blɝ/", senses: blurSenses,
                  matchedForm: MatchedForm(text: "blurred", type: "past tense"))
    }

    // MARK: - Dictionary first

    @Test func dictionaryFormGivesThePillAndTheChip() {
        let word = Explanation.Word(word: "blurred", partOfSpeech: "verb", meaning: "made hard to see")
        let info = SentenceWordInfo(word: word, entry: blurredEntry())
        #expect(info.pillText == "past tense of blur")
        #expect(info.chipText == "verb · A2")
        #expect(info.explanation == "made hard to see")
    }

    @Test func dictionaryWinsOverTheBackendNote() {
        let word = Explanation.Word(word: "blurred", partOfSpeech: "verb", meaning: "made hard to see (base form: blur)")
        let info = SentenceWordInfo(word: word, entry: blurredEntry())
        #expect(info.pillText == "past tense of blur")
        #expect(info.chipText == "verb · A2")
    }

    @Test func baseFormInTheDictionaryHasAChipButNoPill() {
        let word = Explanation.Word(word: "mist", partOfSpeech: "noun", meaning: "a thin cloud near the ground")
        let entry = WordEntry(word: "mist", ipa: nil, senses: blurSenses)
        let info = SentenceWordInfo(word: word, entry: entry)
        #expect(info.pillText == nil)
        #expect(info.chipText == "verb · A2")
    }

    // MARK: - Backend note when the dictionary does not have the word

    @Test func backendNoteIsThePillAndThereIsNoChip() {
        let word = Explanation.Word(word: "edges", partOfSpeech: "noun", meaning: "the outside parts (base form: edge)")
        let info = SentenceWordInfo(word: word, entry: nil)
        #expect(info.pillText == "base form: edge")
        #expect(info.chipText == nil)
        #expect(info.explanation == "the outside parts")
    }

    @Test func noNoteAndNoEntryMeansNoPillAndNoChip() {
        let word = Explanation.Word(word: "mist", partOfSpeech: "noun", meaning: "a thin cloud near the ground")
        let info = SentenceWordInfo(word: word, entry: nil)
        #expect(info.pillText == nil)
        #expect(info.chipText == nil)
        #expect(info.explanation == "a thin cloud near the ground")
    }

    @Test func entryWithoutSensesHasNoChip() {
        let word = Explanation.Word(word: "mist", partOfSpeech: "noun", meaning: "a thin cloud")
        let info = SentenceWordInfo(word: word, entry: WordEntry(word: "mist", ipa: nil, senses: []))
        #expect(info.chipText == nil)
    }

    // MARK: - Reading the note

    @Test func splitsTheNoteFromTheExplanation() {
        let result = SentenceWordInfo.splitBaseFormNote(from: "took out (base form: draw out)")
        #expect(result.explanation == "took out")
        #expect(result.note == "base form: draw out")
    }

    @Test func noteWithoutBracketsAlsoWorks() {
        let result = SentenceWordInfo.splitBaseFormNote(from: "took out, base form: draw out")
        #expect(result.explanation == "took out")
        #expect(result.note == "base form: draw out")
    }

    @Test func meaningWithoutANoteIsUnchanged() {
        let result = SentenceWordInfo.splitBaseFormNote(from: "a thin cloud")
        #expect(result.explanation == "a thin cloud")
        #expect(result.note == nil)
    }

    // MARK: - Tap

    @Test func onlyASingleWordOpensTheWordCard() {
        let single = SentenceWordInfo(word: .init(word: "mist", partOfSpeech: "noun", meaning: "x"), entry: nil)
        let phrase = SentenceWordInfo(word: .init(word: "drew out", partOfSpeech: "phrasal verb", meaning: "x"), entry: nil)
        #expect(single.canOpenWordCard == true)
        #expect(phrase.canOpenWordCard == false)
    }

    // MARK: - SentenceResult

    @Test func resultBuildsOneInfoPerWordUsingTheBundleLookup() {
        let explanation = Explanation(
            meaning: "x",
            words: [
                .init(word: "blurred", partOfSpeech: "verb", meaning: "made hard to see"),
                .init(word: "edges", partOfSpeech: "noun", meaning: "outside parts (base form: edge)"),
            ],
            translation: "y")
        let entry = blurredEntry()
        let result = SentenceResult(
            sentence: "s", explanation: explanation, language: .vietnamese,
            bundledEntry: { $0 == "blurred" ? entry : nil })
        #expect(result.words.map(\.word) == ["blurred", "edges"])
        #expect(result.words[0].pillText == "past tense of blur")
        #expect(result.words[1].pillText == "base form: edge")
        #expect(result.words[1].chipText == nil)
    }
}
