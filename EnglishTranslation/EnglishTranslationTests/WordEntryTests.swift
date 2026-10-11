//
//  WordEntryTests.swift
//  EnglishTranslationTests
//

import Foundation
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
        #expect(WordEntry.sparse.senses[0].vietnamese.isEmpty)
        #expect(WordEntry.sparse.senses[0].usage.isEmpty)
    }

    @Test func plainUsageIsAPatternByDefault() {
        #expect(Usage(pattern: "get + noun", example: "").kind == .pattern)
    }

    @Test func usageGroupsFollowTheFixedOrder() {
        let usage = [
            Usage(kind: .idiom, pattern: "get hold of", example: "a"),
            Usage(kind: .collocation, pattern: "get a job", example: "b"),
            Usage(kind: .pattern, pattern: "get + noun", example: "c"),
            Usage(kind: .phrase, pattern: "get used to", example: "d"),
        ]
        let sense = Sense(pos: "verb", cefr: "A1", simpleDefinition: "x", vietnamese: "y", examples: [], usage: usage)
        let groups = sense.usageGroups
        #expect(groups.map(\.title) == ["Patterns", "Collocations", "Phrases and idioms"])
        #expect(groups[0].items.map(\.pattern) == ["get + noun"])
        // A phrase and an idiom share one group, in their original order.
        #expect(groups[2].items.map(\.pattern) == ["get hold of", "get used to"])
    }

    @Test func emptyUsageGroupsAreLeftOut() {
        let usage = [Usage(kind: .collocation, pattern: "an apple tree", example: "a")]
        let sense = Sense(pos: "noun", cefr: "A1", simpleDefinition: "x", vietnamese: "y", examples: [], usage: usage)
        #expect(sense.usageGroups.map(\.title) == ["Collocations"])
        #expect(WordEntry.sparse.senses[0].usageGroups.isEmpty)
    }

    @Test func formPillTextNamesTheFormTypeBaseWordAndIPA() {
        #expect(WordEntry.ran.formPillText == "past tense of run /rʌn/")
    }

    @Test func formPillTextSkipsMissingIPA() {
        let entry = WordEntry(word: "run", ipa: nil, senses: [],
                              matchedForm: MatchedForm(text: "runs", type: "plural"))
        #expect(entry.formPillText == "plural of run")
    }

    @Test func noPillWhenTheUserTypedTheBaseWord() {
        #expect(WordEntry.depend.formPillText == nil)
    }

    @Test func exampleReadsOldPlainStringsAndNewObjects() throws {
        let json = #"["It rains.", {"level": "harder", "text": "Rain fell all night."}, {"level": "odd", "text": "No level."}]"#
        let examples = try JSONDecoder().decode([Example].self, from: Data(json.utf8))
        #expect(examples == [
            Example(text: "It rains."),
            Example(level: .harder, text: "Rain fell all night."),
            Example(text: "No level."),
        ])
    }

    @Test func oldDataSampleHasExamplesWithoutLevels() {
        let examples = WordEntry.oldData.senses[0].examples
        #expect(examples.count == 2)
        #expect(examples.allSatisfy { $0.level == nil })
    }

    @Test func entryWithoutMatchedFormStillDecodes() throws {
        let json = #"{"word": "run", "ipa": null, "senses": []}"#
        let entry = try JSONDecoder().decode(WordEntry.self, from: Data(json.utf8))
        #expect(entry.matchedForm == nil)
    }

    @Test func getSampleHasAtMostThreeUsageItemsPerSense() {
        #expect(WordEntry.get.senses.allSatisfy { $0.usage.count <= 3 })
    }
}
