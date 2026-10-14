//
//  SentenceResultView.swift
//  EnglishTranslation
//

import SwiftUI

/// A sentence result as cards (TASK-41): the sentence, the Meaning card (translation folded, last),
/// then the Words. The language comes from the result, not from the picker (TASK-14).
struct SentenceResultView: View {
    let result: SentenceResult
    /// Gets the full word card for a tapped word. The real one is `TranslationStore.wordEntry(for:)`.
    var loadWordEntry: (SentenceWordInfo) async throws -> WordEntry = { word in
        guard let entry = word.entry else { throw WordLookupError.notOneWord }
        return entry
    }

    @State private var openedWord: SentenceWordInfo?

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.large) {
            SentenceHeader(sentence: result.sentence)

            MeaningCard(
                meaning: result.explanation.meaning,
                translation: result.explanation.translation,
                language: result.language)

            if !result.words.isEmpty {
                VStack(alignment: .leading, spacing: Theme.Spacing.medium) {
                    Text("Words")
                        .font(.headline)
                        .foregroundStyle(Theme.Colors.textPrimary)
                    ForEach(result.words) { info in
                        SentenceWordCard(info: info) { openedWord = info }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .sheet(item: $openedWord) { info in
            SentenceWordSheet(info: info, targetLanguage: result.language, load: loadWordEntry)
        }
    }
}

#Preview {
    ScrollView {
        SentenceResultView(
            result: SentenceResult(
                sentence: "The mist blurred the edges of the world.",
                explanation: Explanation(
                    meaning: "The fog made the edges of everything hard to see clearly.",
                    words: [
                        .init(word: "mist", partOfSpeech: "noun", meaning: "a thin cloud of water close to the ground"),
                        .init(word: "blurred", partOfSpeech: "verb", meaning: "made something hard to see clearly"),
                        .init(word: "edges", partOfSpeech: "noun", meaning: "the outside parts (base form: edge)"),
                    ],
                    translation: "Màn sương làm nhòe đi các đường biên của thế giới."),
                language: .vietnamese,
                bundledEntry: { word in
                    word == "blurred"
                        ? WordEntry(word: "blur", ipa: "/blɝ/", senses: WordEntry.depend.senses,
                                    matchedForm: MatchedForm(text: "blurred", type: "past tense"))
                        : nil
                }))
            .padding()
    }
    .background(Theme.Colors.background)
    .environment(SpeechPlayer.silent())
}
