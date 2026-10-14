//
//  TextRouteTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

struct TextRouteTests {
    @Test func emptyAndSpacesAreEmpty() {
        #expect(TextRoute(text: "") == .empty)
        #expect(TextRoute(text: "  \n\t ") == .empty)
        #expect(TextRoute(text: "").problemMessage != nil)
    }

    @Test func oneTokenIsAWord() {
        #expect(TextRoute(text: "reluctant") == .word)
        #expect(TextRoute(text: "  Run! ") == .word)
        #expect(TextRoute(text: "don't") == .word)
    }

    @Test func moreThanOneTokenIsASentence() {
        #expect(TextRoute(text: "go home") == .sentence)
        #expect(TextRoute(text: "line one\nline two") == .sentence)
    }

    @Test func exactlyTheLimitIsAllowed() {
        let text = String(repeating: "a ", count: 249) + "aa" // 500 characters
        #expect(text.utf16.count == 500)
        #expect(TextRoute(text: text) == .sentence)
    }

    @Test func overTheLimitIsTooLongWithAClearMessage() {
        let text = String(repeating: "word ", count: 101) // 505 characters, 504 after trimming
        let route = TextRoute(text: text)
        #expect(route == .tooLong(length: 504))
        #expect(route.problemMessage == "This text has 504 characters. The limit is 500. Select a shorter part.")
    }

    @Test func oneLongWordIsTooLongToo() {
        #expect(TextRoute(text: String(repeating: "a", count: 501)) == .tooLong(length: 501))
    }

    @Test func wordsAndSentencesHaveNoProblem() {
        #expect(TextRoute.word.problemMessage == nil)
        #expect(TextRoute.sentence.problemMessage == nil)
    }
}

struct DeepLinkTests {
    @Test func roundTripKeepsTheText() throws {
        let text = "I'd like to \"drew out\" a plan & go; 100% sure? Xin chào"
        let url = try #require(DeepLink.lookupURL(text: text))
        #expect(url.scheme == "saynata")
        #expect(DeepLink.text(from: url) == text)
    }

    @Test func readsAHandWrittenLink() throws {
        let url = try #require(URL(string: "saynata://lookup?text=hello%20world"))
        #expect(DeepLink.text(from: url) == "hello world")
    }

    @Test func otherLinksGiveNoText() throws {
        #expect(DeepLink.text(from: try #require(URL(string: "https://example.com/?text=hi"))) == nil)
        #expect(DeepLink.text(from: try #require(URL(string: "saynata://other?text=hi"))) == nil)
        #expect(DeepLink.text(from: try #require(URL(string: "saynata://lookup"))) == nil)
        #expect(DeepLink.text(from: try #require(URL(string: "saynata://lookup?text=%20"))) == nil)
    }
}
