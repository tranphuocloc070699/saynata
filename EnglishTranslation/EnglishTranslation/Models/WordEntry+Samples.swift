//
//  WordEntry+Samples.swift
//  EnglishTranslation
//

import Foundation

/// Sample entries for previews and the design gallery. Not real dictionary data.
extension WordEntry {

    /// A normal word with two senses. Text from the real dictionary; usage items are invented.
    static let depend = WordEntry(
        word: "depend",
        ipa: "/dɪˈpɛnd/",
        senses: [
            Sense(
                pos: "verb",
                cefr: "A2",
                simpleDefinition: "to be decided or changed by something else, so the result is not the same every time",
                vietnamese: "phụ thuộc vào, tùy thuộc",
                examples: [
                    "We might go to the beach. It depends on the weather.",
                    "The price depends on the size.",
                ],
                usage: [
                    Usage(pattern: "depend on + noun", example: "It depends on the weather."),
                    Usage(pattern: "it depends whether + clause", example: "It depends whether she comes."),
                ]
            ),
            Sense(
                pos: "verb",
                cefr: "B1",
                simpleDefinition: "to need someone or something and feel sure they will help you or do what you want",
                vietnamese: "dựa vào, tin cậy",
                examples: ["You can depend on me."],
                usage: [
                    Usage(pattern: "depend on + someone", example: "I depend on my sister."),
                ]
            ),
        ]
    )

    /// A word whose usage items use every kind of label.
    static let get = WordEntry(
        word: "get",
        ipa: "/ɡɛt/",
        senses: [
            Sense(
                pos: "verb", cefr: "A1",
                simpleDefinition: "to buy, find or receive something",
                vietnamese: "mua, tìm được, nhận được",
                examples: ["I need to get some milk."],
                usage: [
                    Usage(pattern: "get + noun", example: "I need to get some milk."),
                    Usage(kind: .collocation, pattern: "get a job", example: "He got a new job last month."),
                    Usage(kind: .idiom, pattern: "get hold of something", example: "I finally got hold of two tickets."),
                ]
            ),
            Sense(
                pos: "verb", cefr: "A2",
                simpleDefinition: "to become something, or to change into a new state",
                vietnamese: "trở nên",
                examples: ["It's getting dark outside."],
                usage: [
                    Usage(pattern: "get + adjective", example: "It's getting dark outside."),
                    Usage(kind: .collocation, pattern: "get better", example: "I hope you get better soon."),
                    Usage(kind: .phrase, pattern: "get used to + -ing", example: "I'm getting used to living here."),
                ]
            ),
        ]
    )

    /// A word with many senses, to check long scrolling and layout.
    static let run = WordEntry(
        word: "run",
        ipa: "/rʌn/",
        senses: [
            Sense(
                pos: "verb", cefr: "A1",
                simpleDefinition: "to move on your feet fast, faster than walking",
                vietnamese: "chạy",
                examples: ["She runs to school every day."],
                usage: [Usage(pattern: "run + to + place", example: "He ran to the station.")]
            ),
            Sense(
                pos: "verb", cefr: "B1",
                simpleDefinition: "to control a business or a group of people",
                vietnamese: "điều hành, quản lý",
                examples: ["Her parents run a small shop."],
                usage: [Usage(pattern: "run + a business", example: "They run a café.")]
            ),
            Sense(
                pos: "verb", cefr: "B1",
                simpleDefinition: "to work, or to make a machine work",
                vietnamese: "chạy (máy), hoạt động",
                examples: ["The engine is running."]
            ),
            Sense(
                pos: "noun", cefr: "A2",
                simpleDefinition: "an act of running, usually for exercise",
                vietnamese: "buổi chạy bộ",
                examples: ["I go for a run before work."],
                usage: [Usage(pattern: "go for a run", example: "Let's go for a run.")]
            ),
            Sense(
                pos: "verb", cefr: "C1",
                simpleDefinition: "to be shown or printed in a newspaper or on TV",
                vietnamese: "được đăng, được chiếu",
                examples: ["The story ran on the front page."]
            ),
        ]
    )

    /// A rare word with every optional field missing.
    static let sparse = WordEntry(
        word: "zeitgeist",
        ipa: nil,
        senses: [
            Sense(pos: "noun", cefr: nil, simpleDefinition: "the main ideas and feelings of a certain time in history", vietnamese: nil),
        ]
    )
}
