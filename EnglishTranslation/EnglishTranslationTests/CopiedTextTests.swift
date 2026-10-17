//
//  CopiedTextTests.swift
//  EnglishTranslationTests
//

import Testing
@testable import EnglishTranslation

struct CopiedTextTests {
    @Test func booksCitationAndQuotesAreRemoved() {
        let copied = """
        “It was a bright cold day in April.”

        Excerpt From
        Nineteen Eighty-Four
        George Orwell
        This material may be protected by copyright.
        """
        #expect(CopiedText.cleaned(copied) == "It was a bright cold day in April.")
    }

    @Test func oneCopiedWordFromBooks() {
        let copied = "“reluctant”\n\nExcerpt From\nA Book\nAn Author\nThis material may be protected by copyright."
        #expect(CopiedText.cleaned(copied) == "reluctant")
    }

    @Test func plainTextStaysTheSame() {
        #expect(CopiedText.cleaned("  run late \n") == "run late")
        #expect(CopiedText.cleaned("First line.\n\nSecond line.") == "First line.\n\nSecond line.")
    }

    @Test func innerQuotesAreKept() {
        #expect(CopiedText.cleaned("\"Yes\" and \"no\"") == "\"Yes\" and \"no\"")
        #expect(CopiedText.cleaned("\"Go home.\"") == "Go home.")
    }
}
