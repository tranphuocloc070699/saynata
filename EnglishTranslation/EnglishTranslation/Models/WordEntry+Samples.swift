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
                    Example(level: .easy, text: "The price depends on the size."),
                    Example(level: .medium, text: "We might go to the beach. It depends on the weather."),
                    Example(level: .harder, text: "Whether the plan works depends largely on how many people agree to help."),
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
                examples: [
                    Example(level: .easy, text: "I need to get some milk."),
                    Example(level: .medium, text: "She got a letter from her bank."),
                ],
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
                examples: [
                    Example(level: .easy, text: "She runs to school every day."),
                    Example(level: .medium, text: "He ran to the station to catch the train."),
                ],
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

    /// The word "run" as the user typed "ran": the card shows "ran" and the pill "past tense of run".
    static let ran = WordEntry(
        word: "run",
        ipa: "/rʌn/",
        senses: run.senses,
        matchedForm: MatchedForm(text: "ran", type: "past tense")
    )

    /// A word with two senses and examples at every level.
    static let apple = WordEntry(
        word: "apple",
        ipa: "/ˈæpəl/",
        senses: [
            Sense(
                pos: "noun", cefr: "A1",
                simpleDefinition: "a round fruit with red, green or yellow skin that grows on a tree",
                vietnamese: "quả táo",
                examples: [
                    Example(level: .easy, text: "I eat an apple every day."),
                    Example(level: .medium, text: "She cut the apple into four pieces."),
                    Example(level: .harder, text: "The apples in this garden are ready to pick in autumn."),
                ],
                usage: [
                    Usage(kind: .collocation, pattern: "an apple tree", example: "They have an apple tree behind the house."),
                    Usage(kind: .idiom, pattern: "the apple of someone's eye", example: "His daughter is the apple of his eye."),
                ]
            ),
            Sense(
                pos: "noun", cefr: "B2",
                simpleDefinition: "the name of a big technology company that makes phones and computers",
                vietnamese: "công ty Apple",
                examples: [Example(level: .medium, text: "He works for Apple in California.")]
            ),
        ]
    )

    /// Old data: examples are plain strings with no level, and there is no usage.
    static let oldData: WordEntry = {
        let json = """
        {"word": "gentle", "ipa": "/ˈdʒɛntəl/", "senses": [{"pos": "adjective", "cefr": "B1",
         "simpleDefinition": "calm and kind, not rough or loud", "vietnamese": "dịu dàng",
         "examples": ["She has a gentle voice.", "Be gentle with the baby."]}]}
        """
        // Fixed text, so decoding cannot fail.
        return try! JSONDecoder().decode(WordEntry.self, from: Data(json.utf8))
    }()

    /// A rare word with every optional field missing.
    static let sparse = WordEntry(
        word: "zeitgeist",
        ipa: nil,
        senses: [
            Sense(pos: "noun", cefr: "", simpleDefinition: "the main ideas and feelings of a certain time in history", vietnamese: "", examples: []),
        ]
    )
}
