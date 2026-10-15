//
//  TranslationState.swift
//  EnglishTranslation
//
//  Created by Trần Phước Lộc on 7/10/26.
//
import Foundation


enum TranslationState {
    ///Nothing has been requested yet
    case idle
    ///The request is in flight
    case loading
    ///The request worked. Holds the sentence, its explanation and the language it was made in
    case loaded(SentenceResult)
    ///A single word was looked up. Holds the dictionary entry
    case loadedWord(WordEntry)
    ///The request failed. Holds a message the user can read, and "did you mean" words for a misspelled word
    case failed(String, spelling: SpellingHelp? = nil)

}

extension TranslationState {
    /// Changes when the screen shows something else (a new search, a new word or sentence).
    /// Views watch it with `onChange` to stop speech that belongs to the old result.
    var resultKey: String {
        switch self {
        case .idle: "idle"
        case .loading: "loading"
        case let .loaded(result): "sentence:\(result.sentence)"
        case let .loadedWord(entry): "word:\(entry.word)"
        case let .failed(message, _): "failed:\(message)"
        }
    }
}

/// "Did you mean" for a word that was not found: what the user typed and up to 3 close words.
struct SpellingHelp: Equatable {
    let typedWord: String
    let suggestions: [String]
}
