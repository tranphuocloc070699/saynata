//
//  SentenceHeader.swift
//  EnglishTranslation
//

import SwiftUI

/// The top of a sentence result: the sentence in quotes (serif), with Listen and Copy on the right.
struct SentenceHeader: View {
    let sentence: String
    /// True for a moment after Copy, so the icon shows a check mark.
    @State private var didCopy = false

    private var part: SpeechPart { .sentence(sentence) }

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.small) {
            Text("\u{201C}\(sentence)\u{201D}")
                .font(.system(.title3, design: .serif))
                .foregroundStyle(Theme.Colors.textPrimary)
                .textSelection(.enabled)
                .speechHighlight(part)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, Theme.Spacing.small)

            SpeakerButton(part: part)
            IconButton(systemImage: didCopy ? "checkmark" : "doc.on.doc", label: "Copy", action: copy)
        }
    }

    private func copy() {
        UIPasteboard.general.string = sentence
        didCopy = true
        Task {
            try? await Task.sleep(for: .seconds(1.5))
            didCopy = false
        }
    }
}

#Preview {
    SentenceHeader(sentence: "The mist blurred the edges of the world.")
        .padding()
        .background(Theme.Colors.background)
}
