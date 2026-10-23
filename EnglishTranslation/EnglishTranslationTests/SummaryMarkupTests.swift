//
//  SummaryMarkupTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct SummaryMarkupTests {
    private func substrings(_ ranges: [NSRange], in text: String) -> [String] {
        ranges.map { (text as NSString).substring(with: $0) }
    }

    @Test func aPlainLineStaysAsItIs() {
        let markup = SummaryMarkup("I do not know what to choose.")

        #expect(markup.text == "I do not know what to choose.")
        #expect(markup.boldRanges.isEmpty)
        #expect(markup.bulletRanges.isEmpty)
    }

    @Test func bulletsAndBoldLoseTheirMarkers() {
        let markup = SummaryMarkup("Your **career** is at risk.\n- Use **private distribution**.\n- Leave.")

        #expect(markup.text == "Your career is at risk.\n\u{2022}\tUse private distribution.\n\u{2022}\tLeave.")
        #expect(substrings(markup.boldRanges, in: markup.text) == ["career", "private distribution"])
        #expect(substrings(markup.bulletRanges, in: markup.text) == ["\u{2022}\tUse private distribution.", "\u{2022}\tLeave."])
    }

    @Test func tappedWordsAreFoundInTheShownText() {
        let markup = SummaryMarkup("- **Apple** bans apps.")
        let words = SentenceTokenizer.tokens(in: markup.text).map(\.text)

        #expect(words == ["Apple", "bans", "apps"])
    }

    @Test func anOpenBoldWhileStreamingMakesTheRestBoldAndHidesTheMarker() {
        let markup = SummaryMarkup("A ban could hurt your **care")

        #expect(markup.text == "A ban could hurt your care")
        #expect(substrings(markup.boldRanges, in: markup.text) == ["care"])
    }

    @Test func plainTextHasNoBulletsForListenAndLiked() {
        #expect(SummaryMarkup("Gist.\n- **One** idea.\n- Two.").plainText == "Gist.\nOne idea.\nTwo.")
    }

    @Test func theSentenceTheUserTypedIsNeverRead() {
        let markup = SummaryMarkup(plain: "- **not** markdown")

        #expect(markup.text == "- **not** markdown")
        #expect(markup.boldRanges.isEmpty)
    }

    @Test func theAttributedStringKeepsBoldAndShowsBullets() {
        let attributed = SummaryMarkup("Gist.\n- **Key** idea.").attributedString
        let boldText = attributed.runs
            .filter { $0.inlinePresentationIntent == .stronglyEmphasized }
            .map { String(attributed[$0.range].characters) }

        #expect(String(attributed.characters) == "Gist.\n\u{2022} Key idea.")
        #expect(boldText == ["Key"])
    }
}
