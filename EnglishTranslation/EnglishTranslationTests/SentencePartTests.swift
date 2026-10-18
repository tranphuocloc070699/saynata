//
//  SentencePartTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct SentencePartTests {
    private let sentence = "The mist blurred the edges of the world, so the hikers turned back."

    private func part(_ selected: String) -> SentencePart? {
        let range = (sentence as NSString).range(of: selected)
        return SentencePart.from(range: range, in: sentence)
    }

    @Test func severalWordsGiveAPhrase() {
        #expect(part("blurred the edges") == .phrase("blurred the edges"))
    }

    @Test func spacesAndCommasAtTheEdgesAreTrimmed() {
        #expect(part(" blurred the edges of the world, ") == .phrase("blurred the edges of the world"))
    }

    @Test func oneWholeWordGivesTheWord() {
        guard case let .word(token) = part("mist") else {
            Issue.record("Expected .word")
            return
        }
        #expect(token.text == "mist")
        #expect(token.lookupWord == "mist")
    }

    @Test func oneWordWithItsSpaceStillGivesTheWord() {
        guard case let .word(token) = part("mist ") else {
            Issue.record("Expected .word")
            return
        }
        #expect(token.text == "mist")
    }

    @Test func partOfAWordIsAPhrase() {
        // "blur" inside "blurred" is not a whole word: the popup explains it.
        #expect(part("blur") == .phrase("blur"))
    }

    @Test func oneCharacterIsTooShort() {
        #expect(part("T") == nil)
        // A space and one letter is one character after trimming.
        #expect(part(" T") == nil)
    }

    @Test func twoCharactersAreEnough() {
        #expect(part("of") != nil)
    }

    @Test func marksOnlyGiveNothing() {
        let text = "Yes, it is..."
        #expect(SentencePart.from(range: NSRange(location: 10, length: 3), in: text) == nil)
    }

    @Test func emptyOrOutOfRangeSelectionsGiveNothing() {
        #expect(SentencePart.from(range: NSRange(location: 0, length: 0), in: sentence) == nil)
        #expect(SentencePart.from(range: NSRange(location: NSNotFound, length: 0), in: sentence) == nil)
        #expect(SentencePart.from(range: NSRange(location: 60, length: 100), in: sentence) == nil)
    }

    @Test func lineBreaksInsideAPhraseBecomeOneSpace() {
        let text = "turned\nback now"
        let part = SentencePart.from(range: NSRange(location: 0, length: 11), in: text)
        #expect(part == .phrase("turned back"))
    }
}

@MainActor
struct SelectionDebouncerTests {
    @Test func onlyTheLastChangeRuns() async throws {
        let debouncer = SelectionDebouncer(delay: .milliseconds(30))
        var ran: [Int] = []
        debouncer.submit { ran.append(1) }
        debouncer.submit { ran.append(2) }
        debouncer.submit { ran.append(3) }
        try await Task.sleep(for: .milliseconds(200))
        #expect(ran == [3])
    }

    @Test func cancelStopsTheWaitingChange() async throws {
        let debouncer = SelectionDebouncer(delay: .milliseconds(30))
        var ran = false
        debouncer.submit { ran = true }
        debouncer.cancel()
        try await Task.sleep(for: .milliseconds(150))
        #expect(ran == false)
    }
}
