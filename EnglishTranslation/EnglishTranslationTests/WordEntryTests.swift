//
//  WordEntryTests.swift
//  EnglishTranslationTests
//

import Testing
@testable import EnglishTranslation

struct WordEntryTests {

    @Test func chipTextJoinsPartOfSpeechAndLevel() {
        #expect(WordEntry.depend.senses[0].chipText == "verb · A2")
    }

    @Test func chipTextIsJustPartOfSpeechWhenLevelIsMissing() {
        #expect(WordEntry.sparse.senses[0].chipText == "noun")
    }

    @Test func sampleWordsCoverTheCases() {
        #expect(WordEntry.depend.senses.count == 2)
        #expect(WordEntry.run.senses.count >= 4)
        #expect(WordEntry.sparse.ipa == nil)
        #expect(WordEntry.sparse.senses[0].vietnamese == nil)
        #expect(WordEntry.sparse.senses[0].usage.isEmpty)
    }

    @Test func plainPatternHasNoLabel() {
        #expect(Usage.Kind.pattern.label == nil)
        #expect(Usage(pattern: "get + noun", example: "").kind == .pattern)
    }

    @Test func otherKindsUseTheirNameAsLabel() {
        #expect(Usage.Kind.collocation.label == "collocation")
        #expect(Usage.Kind.phrase.label == "phrase")
        #expect(Usage.Kind.idiom.label == "idiom")
    }

    @Test func getSampleHasAtMostThreeUsageItemsPerSense() {
        #expect(WordEntry.get.senses.allSatisfy { $0.usage.count <= 3 })
    }
}
