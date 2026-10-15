//
//  SentenceWordFinder.swift
//  EnglishTranslation
//

import Foundation
import NaturalLanguage

/// Finds the content words of a sentence that are in the bundled dictionary (TASK-56).
///
/// Claude only lists the words that are hard for a B1 learner. This adds the other words,
/// so "I like apples" is not shown with an empty Words list. It works offline.
enum SentenceWordFinder {
    /// Only these kinds of words are kept. Pronouns, articles, prepositions, conjunctions and numbers are not.
    private static let contentClasses: Set<NLTag> = [.noun, .verb, .adjective, .adverb]

    /// Helper verbs. "be", "have" and "do" mostly help other verbs, and modals ("can", "will") always do.
    private static let helperWords: Set<String> = [
        "be", "have", "do", "can", "could", "will", "would", "shall", "should", "may", "might", "must",
    ]

    /// The extra words, in sentence order, as word-card data.
    ///
    /// - Parameters:
    ///   - sentence: The sentence the user sent.
    ///   - claudeWords: The words Claude listed. They are never repeated here.
    ///   - bundledEntry: Looks a word up in the bundled dictionary. Forms work too ("ran" finds "run").
    static func extraWords(
        in sentence: String,
        skipping claudeWords: [SentenceWordInfo],
        bundledEntry: (String) -> WordEntry?
    ) -> [SentenceWordInfo] {
        // Everything Claude already covers: its own spelling and the base form of each word.
        var seen = Set<String>()
        for info in claudeWords {
            seen.insert(info.word.lowercased())
            if let lemma = info.entry?.word { seen.insert(lemma.lowercased()) }
        }

        var found: [SentenceWordInfo] = []
        for token in contentTokens(in: sentence) {
            let spelling = token.lowercased()
            guard !seen.contains(spelling),
                  let entry = bundledEntry(token),
                  !entry.senses.isEmpty,
                  !helperWords.contains(entry.word.lowercased()),
                  !seen.contains(entry.word.lowercased())
            else { continue }
            seen.insert(spelling)
            seen.insert(entry.word.lowercased())
            found.append(SentenceWordInfo(sentenceWord: token, entry: entry))
        }
        return found
    }

    /// The nouns, verbs, adjectives and adverbs of the sentence, in order.
    private static func contentTokens(in sentence: String) -> [String] {
        let tagger = NLTagger(tagSchemes: [.lexicalClass])
        tagger.string = sentence
        tagger.setLanguage(.english, range: sentence.startIndex..<sentence.endIndex)

        var tokens: [String] = []
        tagger.enumerateTags(
            in: sentence.startIndex..<sentence.endIndex,
            unit: .word,
            scheme: .lexicalClass,
            options: [.omitWhitespace, .omitPunctuation, .omitOther]
        ) { tag, range in
            if let tag, contentClasses.contains(tag) {
                tokens.append(String(sentence[range]))
            }
            return true
        }
        return tokens
    }
}
