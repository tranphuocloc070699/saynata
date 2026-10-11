//
//  DictionaryStoreTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// These tests read the real `dictionary.sqlite` that is bundled in the app.
struct DictionaryStoreTests {
    @Test func bundledDictionaryOpens() throws {
        _ = try DictionaryStore.bundled()
    }

    @Test func knownWordIsFound() throws {
        let entry = try #require(try DictionaryStore.bundled().lookUp("run"))
        #expect(entry.word == "run")
        #expect(entry.ipa != nil)
        let first = try #require(entry.senses.first)
        #expect(first.pos == "verb")
        #expect(!first.simpleDefinition.isEmpty)
        #expect(!first.vietnamese.isEmpty)
        #expect(first.examples.count == 2)
        #expect(first.usage.isEmpty)
    }

    @Test func wordFormFindsTheBaseWord() throws {
        let entry = try #require(try DictionaryStore.bundled().lookUp("ran"))
        #expect(entry.word == "run")
    }

    @Test func inputIsNormalisedBeforeLookup() throws {
        let entry = try #require(try DictionaryStore.bundled().lookUp("  RUN \n"))
        #expect(entry.word == "run")
    }

    @Test func unknownWordIsNil() throws {
        let store = try DictionaryStore.bundled()
        #expect(try store.lookUp("zzzzqq") == nil)
        #expect(try store.lookUp("   ") == nil)
    }

    @Test func usageKindDefaultsToPattern() throws {
        let json = #"{"pattern": "run out of", "example": "We ran out of milk."}"#
        let usage = try JSONDecoder().decode(Usage.self, from: Data(json.utf8))
        #expect(usage.kind == .pattern)
        let idiom = #"{"kind": "idiom", "pattern": "a", "example": "b"}"#
        #expect(try JSONDecoder().decode(Usage.self, from: Data(idiom.utf8)).kind == .idiom)
    }
}
