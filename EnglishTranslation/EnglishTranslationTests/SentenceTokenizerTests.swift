//
//  SentenceTokenizerTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct SentenceTokenizerTests {
    private func words(_ text: String) -> [String] {
        SentenceTokenizer.tokens(in: text).map(\.text)
    }

    @Test func splitsWordsAndDropsPunctuation() {
        #expect(words("The mist blurred the edges, so we left.") == ["The", "mist", "blurred", "the", "edges", "so", "we", "left"])
    }

    @Test func keepsApostrophesAndHyphensInsideAWord() {
        #expect(words("Don't use a well-known word") == ["Don't", "use", "a", "well-known", "word"])
        #expect(words("It\u{2019}s fine") == ["It\u{2019}s", "fine"])
    }

    @Test func aHyphenOrApostropheAtTheEdgeIsNotPartOfTheWord() {
        #expect(words("'quoted' - dash") == ["quoted", "dash"])
    }

    @Test func numbersAloneAreNotWords() {
        #expect(words("In 2024 we ran 5 km") == ["In", "we", "ran", "km"])
    }

    @Test func aWordWithDigitsAndLettersIsAWord() {
        #expect(words("Take the B2 test") == ["Take", "the", "B2", "test"])
    }

    @Test func rangesPointAtTheWordInUTF16Units() {
        let text = "Hi \u{1F600} there"
        let tokens = SentenceTokenizer.tokens(in: text)
        #expect(tokens.map(\.text) == ["Hi", "there"])
        // The emoji takes two UTF-16 units, so "there" starts at 6, not 5.
        #expect(tokens[1].range == NSRange(location: 6, length: 5))
        #expect((text as NSString).substring(with: tokens[1].range) == "there")
    }

    @Test func tokenAtAnOffsetFindsTheWordOrNil() {
        let tokens = SentenceTokenizer.tokens(in: "The mist")
        #expect(SentenceTokenizer.token(at: 1, in: tokens)?.text == "The")
        #expect(SentenceTokenizer.token(at: 3, in: tokens) == nil) // the space
        #expect(SentenceTokenizer.token(at: 7, in: tokens)?.text == "mist")
        #expect(SentenceTokenizer.token(at: 8, in: tokens) == nil) // past the end
    }

    @Test func lookupWordIsLowercaseWithoutPossessive() {
        let tokens = SentenceTokenizer.tokens(in: "The World's Blurred edge, don\u{2019}t")
        #expect(tokens.map(\.lookupWord) == ["the", "world", "blurred", "edge", "don't"])
    }

    @Test func emptyAndMarksOnlyTextHaveNoWords() {
        #expect(words("") == [])
        #expect(words("?! ...") == [])
    }
}
