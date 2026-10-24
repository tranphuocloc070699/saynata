//
//  SentenceLanguageLineTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

/// TASK-106.6: the backend sends "" (never null) for a sentence with no language line.
@MainActor
struct SentenceLanguageLineTests {
    @Test func emptyOrBlankLineShowsNoRow() {
        #expect(!SentenceCard.hasLanguageLine(""))
        #expect(!SentenceCard.hasLanguageLine("  \n"))
        #expect(SentenceCard.hasLanguageLine("Xin chào"))
    }

    @Test func emptyTranslationStillDecodes() throws {
        let json = #"{"meaning":"A greeting.","words":[],"translation":""}"#
        let explanation = try JSONDecoder().decode(Explanation.self, from: Data(json.utf8))
        #expect(explanation.translation.isEmpty)
    }

    @Test func anAnswerWithoutALineIsNotKeptOnThePhoneForOtherLanguages() {
        let noLine = Explanation(meaning: "A greeting.", words: [], translation: "")
        let withLine = Explanation(meaning: "A greeting.", words: [], translation: "Xin chào")
        #expect(!BackendClient.isComplete(noLine, for: .vietnamese))
        #expect(BackendClient.isComplete(withLine, for: .vietnamese))
        #expect(BackendClient.isComplete(noLine, for: .english))
    }
}
