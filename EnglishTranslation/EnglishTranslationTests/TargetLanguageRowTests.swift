//
//  TargetLanguageRowTests.swift
//  EnglishTranslationTests
//

import Testing
@testable import EnglishTranslation

@MainActor
struct TargetLanguageRowTests {

    private let sense = WordEntry.depend.senses[0]

    @Test func rowTitleIsThePickedLanguage() {
        #expect(TargetLanguage.korean.title == "Korean")
        #expect(TargetLanguage.japanese.title == "Japanese")
        #expect(TargetLanguage.vietnamese.title == "Vietnamese")
    }

    @Test func vietnameseUsesTheDictionaryText() {
        #expect(sense.dictionaryText(for: .vietnamese) == sense.vietnamese)
        #expect(!TargetLanguage.vietnamese.needsOnDeviceTranslation)
    }

    @Test func otherLanguagesAreTranslatedOnTheDevice() {
        #expect(sense.dictionaryText(for: .korean) == nil)
        #expect(TargetLanguage.korean.needsOnDeviceTranslation)
        #expect(sense.hasTargetLanguageRow(for: .korean))
    }

    @Test func englishGetsNoRow() {
        #expect(!sense.hasTargetLanguageRow(for: .english))
    }

    @Test func vietnameseRowStaysWithoutDictionaryTextBecauseTheBackendMakesTheLine() {
        #expect(WordEntry.sparse.senses[0].hasTargetLanguageRow(for: .vietnamese))
        #expect(WordEntry.sparse.senses[0].hasTargetLanguageRow(for: .korean))
    }

    @Test func chineseUsesTheAppleSimplifiedCode() {
        #expect(TargetLanguage.chinese.appleIdentifier == "zh-Hans")
        #expect(TargetLanguage.korean.appleIdentifier == "ko")
    }

    @Test func cacheKeepsOneTranslationPerTextAndLanguage() {
        let cache = TranslationCache()
        #expect(cache.translation(of: "to rely on", in: .korean) == nil)
        cache.save("의존하다", of: "to rely on", in: .korean)
        #expect(cache.translation(of: "to rely on", in: .korean) == "의존하다")
        #expect(cache.translation(of: "to rely on", in: .japanese) == nil)
        #expect(cache.translation(of: "other text", in: .korean) == nil)
    }
}
