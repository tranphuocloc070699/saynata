//
//  Translating.swift
//  EnglishTranslation
//

import Foundation

/// Anything that can explain a sentence. The LLM clients are the real ones; tests use a fake.
protocol Translating: Sendable {
    /// Explains `text` in simple English, with a translation into `language`.
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation
}

/// An LLM API that answers one system prompt plus one user message with text.
/// Each client only does the HTTP part; `explain` is shared below.
protocol ChatCompleting: Translating {
    /// Sends the prompts and returns the model's raw text reply.
    func reply(system: String, user: String) async throws -> String
}

extension ChatCompleting {
    func explain(_ text: String, translatingTo language: TargetLanguage) async throws -> Explanation {
        let raw = try await reply(
            system: TranslationPrompt.system(translatingTo: language.title),
            user: text)
        return try Explanation.decode(fromModelReply: raw)
    }
}

/// The instructions every provider gets, so all of them answer in the same JSON shape.
enum TranslationPrompt {
    /// The system prompt, translating into `language`. Kept in line with `SENTENCE_PROMPT` in the backend.
    static func system(translatingTo language: String) -> String {
        """
        You help a person learning English understand an English sentence.
        Use simple English: common words and short sentences.

        Reply with JSON only. No Markdown, no text before or after. Use this shape:
        {"meaning": "...", "words": [{"word": "...", "partOfSpeech": "...", "meaning": "..."}], "translation": "..."}

        - meaning: explain the whole sentence in simple English. One to three short sentences.
        - words: only words or phrases that are difficult for an intermediate (B1) learner. Write them as they appear \
        in the sentence. Use an empty array if there are none.
        - partOfSpeech: one of noun, verb, phrasal verb, adjective, adverb, preposition, conjunction, pronoun, idiom.
        - words[].meaning: a short explanation in simple English. For a past or -ing form, also give the base form.
        - translation: a natural translation of the whole sentence into \(language).
        """
    }
}
