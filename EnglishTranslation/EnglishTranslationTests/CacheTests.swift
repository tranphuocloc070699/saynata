//
//  CacheTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// The expected keys were computed with `printf '%s' "<parts joined by |>" | shasum -a 256`,
/// the same rule as `CacheKeys.key` in the backend (`CacheKeysTest.java`).
struct CacheKeysTests {
    @Test func sentenceKeyMatchesTheBackendRule() {
        #expect(CacheKeys.sentenceKey("I was reluctant to leave.", targetLang: "vi")
            == "1bbb0b83f351c91ca7b9edab960c522812c7596b4a46d512e43d37d1291c885f")
        // The "v1" key from CacheKeysTest.java has no language part, so use the raw function.
        #expect(CacheKeys.key("sentence", "v1", "hello")
            == "c06d91029579e1afa519f6dde66066df0c1aa512806a052cb168c11b3d098aba")
    }

    @Test func sentenceKeyNormalisesBeforeHashing() {
        #expect(CacheKeys.sentenceKey("  I   love\n US  ", targetLang: "ja")
            == "6ac8a452d346662cc9c8292e9587583a004302a265af6861ebeed72dcbaccfd3")
        #expect(CacheKeys.sentenceKey("I love US", targetLang: "ja")
            == CacheKeys.sentenceKey("I love\tUS", targetLang: "ja"))
    }

    @Test func wordKeyMatchesTheBackendRule() {
        #expect(CacheKeys.wordKey(" Reluctant ")
            == "9749a124498a852c62dea4c4fc1a2e205901d82ca9081362a6b37243e2620f0a")
    }

    @Test func normalisingMatchesJava() {
        #expect(CacheKeys.normalizeSentence("  I   love\n US  ") == "I love US")
        #expect(CacheKeys.normalizeWord(" Reluctant ") == "reluctant")
        #expect(CacheKeys.normalizeSentence("   ") == "")
        // Java's strip() trims an ideographic space, but \s+ does not collapse it.
        #expect(CacheKeys.normalizeSentence("\u{3000}a\u{3000}\u{3000}b\u{3000}") == "a\u{3000}\u{3000}b")
        // A no-break space is not whitespace in Java, so it stays.
        #expect(CacheKeys.normalizeSentence("\u{00A0}a") == "\u{00A0}a")
    }

    @Test func tokenRuleMatchesTheBackendNormalising() {
        // One token after normalising: a word.
        #expect(CacheKeys.isSingleToken("run"))
        #expect(CacheKeys.isSingleToken("  Ran \n"))
        #expect(CacheKeys.isSingleToken("don't"))
        #expect(CacheKeys.isSingleToken("well-known"))
        // A no-break space is not whitespace for Java's \s+, so it does not split a token.
        #expect(CacheKeys.isSingleToken("a\u{00A0}b"))
        // More than one token, or none: not a word.
        #expect(CacheKeys.isSingleToken("go home") == false)
        #expect(CacheKeys.isSingleToken("go\n\thome") == false)
        #expect(CacheKeys.isSingleToken("   ") == false)
        #expect(CacheKeys.isSingleToken("") == false)
    }

    @Test func wordPunctuationIsTrimmedAtBothEnds() {
        #expect(CacheKeys.trimmingWordPunctuation("Hello.") == "Hello")
        #expect(CacheKeys.trimmingWordPunctuation("run!") == "run")
        #expect(CacheKeys.trimmingWordPunctuation("(word)") == "word")
        #expect(CacheKeys.trimmingWordPunctuation("\u{201C}word\u{201D}") == "word")
        #expect(CacheKeys.trimmingWordPunctuation("  \"run\"?! ") == "run")
        // Inner apostrophes and hyphens stay.
        #expect(CacheKeys.trimmingWordPunctuation("don't") == "don't")
        #expect(CacheKeys.trimmingWordPunctuation("well-known.") == "well-known")
        // Nothing left.
        #expect(CacheKeys.trimmingWordPunctuation("?!") == "")
        #expect(CacheKeys.trimmingWordPunctuation("  ") == "")
        // Still one token or a sentence by the normal rule.
        #expect(CacheKeys.isSingleToken("Hello."))
        #expect(CacheKeys.isSingleToken("Hello there.") == false)
    }

    @Test func caseAndKindChangeTheKey() {
        #expect(CacheKeys.sentenceKey("US", targetLang: "vi") != CacheKeys.sentenceKey("us", targetLang: "vi"))
        #expect(CacheKeys.sentenceKey("run", targetLang: "vi") != CacheKeys.wordKey("run"))
        #expect(CacheKeys.wordKey("Run") == CacheKeys.wordKey("run"))
    }

    @Test func promptVersionsMatchTheBackendConfig() {
        // application.properties is in the repo next to the iOS project. If the file is not
        // reachable (for example the test runs on a device), the check is skipped.
        let file = URL(filePath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appending(path: "backend/src/main/resources/application.properties")
        guard let text = try? String(contentsOf: file, encoding: .utf8) else { return }
        #expect(text.contains("app.claude.sentence-prompt-version=\(PromptVersions.sentence)"))
        #expect(text.contains("app.claude.word-prompt-version=\(PromptVersions.word)"))
    }
}

struct ResultCacheTests {
    @Test func savedDataComesBackUnchanged() async throws {
        let cache = try ResultCache(fileURL: nil)
        let json = Data(#"{"word":"run","cached":false}"#.utf8)

        await cache.save(json, forKey: "key-1", kind: .word)

        #expect(await cache.data(forKey: "key-1") == json)
        #expect(await cache.data(forKey: "other") == nil)
    }

    @Test func savingTheSameKeyReplacesTheOldAnswer() async throws {
        let cache = try ResultCache(fileURL: nil)
        await cache.save(Data("old".utf8), forKey: "k", kind: .sentence)
        await cache.save(Data("new".utf8), forKey: "k", kind: .sentence)

        #expect(await cache.data(forKey: "k") == Data("new".utf8))
    }

    @Test func fileCacheKeepsAnswersAfterReopening() async throws {
        // The folder name has a space on purpose (see swift.md, File paths).
        let folder = FileManager.default.temporaryDirectory.appending(path: "cache test \(UUID().uuidString)")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let file = folder.appending(path: "result-cache.sqlite")

        let first = try ResultCache(fileURL: file)
        await first.save(Data("kept".utf8), forKey: "k", kind: .word)

        let second = try ResultCache(fileURL: file)
        #expect(await second.data(forKey: "k") == Data("kept".utf8))
    }
}
