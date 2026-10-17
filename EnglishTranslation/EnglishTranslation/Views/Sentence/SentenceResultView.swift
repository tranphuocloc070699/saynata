//
//  SentenceResultView.swift
//  EnglishTranslation
//

import SwiftUI

/// A sentence result (TASK-41, TASK-89.2): one `SentenceCard` with the sentence, its simple summary, and the
/// target language folded last. The language comes from the result, not from the picker (TASK-14).
struct SentenceResultView: View {
    let result: SentenceResult
    /// Gets the full word card for a tapped word. The real one is `TranslationStore.wordEntry(for:)`.
    var loadWordEntry: (SentenceWordInfo) async throws -> WordEntry = { word in
        guard let entry = word.entry else { throw WordLookupError.notOneWord }
        return entry
    }

    var body: some View {
        SentenceCard(
            sentence: result.sentence,
            summary: result.explanation.meaning,
            translation: result.explanation.translation,
            language: result.language,
            loadWordEntry: loadWordEntry)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    ScrollView {
        SentenceResultView(
            result: SentenceResult(
                sentence: "The mist blurred the edges of the world, so the hikers turned back before noon.",
                explanation: Explanation(
                    meaning: "The fog made everything hard to see, so the walkers went back before midday.",
                    words: [],
                    translation: "Sương mù làm mờ mọi thứ, nên những người đi bộ quay lại trước buổi trưa."),
                language: .vietnamese),
            loadWordEntry: { _ in .depend })
            .padding()
    }
    .background(Theme.Colors.background)
    .environment(SpeechPlayer.silent())
}
