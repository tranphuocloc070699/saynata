//
//  SpeechPart.swift
//  EnglishTranslation
//

import Foundation

/// One thing the app can read aloud: its id (to know what plays), its text, and what kind it is.
/// F3 reads only these: the word, a sense's simple explanation, an example, and the sentence.
/// Never the Vietnamese line or the Grammar examples.
struct SpeechPart: Equatable {
    enum Kind {
        case word, explanation, example, sentence

        /// VoiceOver label while the part is not playing.
        var listenLabel: String {
            switch self {
            case .word: "Listen to the word"
            case .explanation: "Listen to the explanation"
            case .example: "Listen to the example"
            case .sentence: "Listen to the sentence"
            }
        }

        /// VoiceOver label while the part is playing.
        var stopLabel: String {
            switch self {
            case .word: "Stop listening to the word"
            case .explanation: "Stop listening to the explanation"
            case .example: "Stop listening to the example"
            case .sentence: "Stop listening to the sentence"
            }
        }
    }

    let id: String
    let text: String
    let kind: Kind

    /// The word as typed: "ran" stays "ran", not "run".
    static func word(of entry: WordEntry) -> SpeechPart {
        SpeechPart(id: "word:\(entry.word)", text: entry.matchedForm?.text ?? entry.word, kind: .word)
    }

    /// The simple English explanation of one sense.
    static func explanation(of sense: Sense, senseIndex: Int, in entry: WordEntry) -> SpeechPart {
        SpeechPart(id: "explanation:\(entry.word):\(senseIndex)", text: sense.simpleDefinition, kind: .explanation)
    }

    /// One example sentence of one sense.
    static func example(_ example: Example, exampleIndex: Int, senseIndex: Int, in entry: WordEntry) -> SpeechPart {
        SpeechPart(id: "example:\(entry.word):\(senseIndex):\(exampleIndex)", text: example.text, kind: .example)
    }

    /// The whole sentence the user typed.
    static func sentence(_ text: String) -> SpeechPart {
        SpeechPart(id: "sentence:\(text)", text: text, kind: .sentence)
    }
}
