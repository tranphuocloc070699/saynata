//
//  WordExamplesSection.swift
//  EnglishTranslation
//

import SwiftUI

/// The content of the "Examples" row: one example per block.
/// Line 1 is the level label (left) and a plain speaker icon (right edge). Line 2 is the sentence at full width.
/// An example with no level (old data) has no label, but still has the speaker.
struct WordExamplesSection: View {
    let examples: [Example]
    /// Which sense these examples belong to.
    let senseIndex: Int

    @Environment(SpeechPlayer.self) private var speechPlayer

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.small) {
            ForEach(Array(examples.enumerated()), id: \.offset) { index, example in
                let speechID = "example-\(senseIndex)-\(index)"
                let isPlaying = speechPlayer.currentID == speechID
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: Theme.Spacing.small) {
                        if let level = example.level {
                            ExampleLevelLabel(level: level)
                        }
                        Spacer(minLength: 0)
                        IconButton(
                            systemImage: isPlaying ? "stop.fill" : "speaker.wave.2",
                            label: isPlaying ? "Stop" : "Listen to the example",
                            style: .plain,
                            isActive: isPlaying
                        ) {
                            if isPlaying {
                                speechPlayer.stop()
                            } else {
                                speechPlayer.speak(example.text, id: speechID)
                            }
                        }
                    }
                    Text(example.text)
                        .font(.system(.callout, design: .serif).italic())
                        .foregroundStyle(Theme.Colors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                        .speechHighlight(isPlaying: isPlaying)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// "easy", "medium" or "harder" in a small tinted capsule.
struct ExampleLevelLabel: View {
    let level: Example.Level

    private var color: Color {
        switch level {
        case .easy: Theme.Colors.levelEasy
        case .medium: Theme.Colors.levelMedium
        case .harder: Theme.Colors.levelHarder
        }
    }

    var body: some View {
        Text(level.label)
            .font(.caption.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, Theme.Spacing.small)
            .padding(.vertical, 2)
            .background(color.opacity(0.14), in: Capsule())
    }
}

#Preview {
    WordExamplesSection(examples: WordEntry.apple.senses[0].examples, senseIndex: 0)
        .padding()
        .environment(SpeechPlayer.silent())
}
