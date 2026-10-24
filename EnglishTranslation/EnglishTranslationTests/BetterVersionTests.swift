//
//  BetterVersionTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

private struct FixingTranslator: Translating {
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        Explanation(
            meaning: "You want someone to make the room bright.", words: [],
            better: "Please turn on the light. It's too dark.",
            betterWhy: "In English you \"turn on\" a light.", translation: "Bật đèn lên.")
    }
}

/// "Better way to say it" (TASK-110): which words are bold, and when the row is offered.
@MainActor
struct BetterVersionTests {
    private func changedWords(_ original: String, _ better: String) -> [String] {
        BetterDiff(original: original, better: better).pieces
            .filter(\.isChanged)
            .map { $0.text.trimmingCharacters(in: .whitespaces) }
    }

    @Test func boldsOnlyTheChangedWords() {
        #expect(changedWords("I already do it.", "I've already done it.") == ["I've", "done"])
        #expect(changedWords("Please open the light, it's too dark.", "Please turn on the light. It's too dark.")
            == ["turn", "on"])
    }

    @Test func thePiecesJoinBackToTheFixedText() {
        let better = "Can you  lend me your pen?"
        let diff = BetterDiff(original: "Can you borrow me your pen?", better: better)
        #expect(diff.pieces.map(\.text).joined() == better)
    }

    @Test func curlyApostrophesAndCaseAreNotChanges() {
        #expect(changedWords("i don’t know", "I don't know.").isEmpty)
    }

    @Test func typedTextKeepsTheFix() async {
        let store = TranslationStore(makeTranslator: { FixingTranslator() })
        store.inputText = "Please open the light, it's too dark."
        await store.translate()
        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(result.isTyped)
        #expect(result.explanation.better == "Please turn on the light. It's too dark.")
    }

    @Test func textFromAnotherAppIsNotTyped() async {
        let store = TranslationStore(makeTranslator: { FixingTranslator() })
        store.useTextFromElsewhere("Please open the light, it's too dark.")
        await store.translate()
        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(result.isTyped == false)
    }

    @Test func editingTextFromAnotherAppMakesItTyped() async {
        let store = TranslationStore(makeTranslator: { FixingTranslator() })
        store.useTextFromElsewhere("Please open the light.")
        store.inputText = "Please open the light, it's too dark."
        await store.translate()
        guard case let .loaded(result) = store.state else {
            Issue.record("Expected .loaded, got \(store.state)")
            return
        }
        #expect(result.isTyped)
    }

    @Test func oldAnswersWithoutTheFieldsDecode() throws {
        let json = #"{"meaning":"Hi.","words":[],"translation":"Chào"}"#
        let explanation = try JSONDecoder().decode(Explanation.self, from: Data(json.utf8))
        #expect(explanation.better == nil)
        #expect(explanation.betterWhy == nil)
    }
}
