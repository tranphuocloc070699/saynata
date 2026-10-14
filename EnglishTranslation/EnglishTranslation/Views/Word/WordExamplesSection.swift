//
//  WordExamplesSection.swift
//  EnglishTranslation
//

import SwiftUI

/// The content of the "Examples" row: one example per line, each with a small level label and a small speaker at the end.
/// An example with no level (old data) has no label.
struct WordExamplesSection: View {
    let examples: [Example]
    /// Which sense these examples belong to.
    let senseIndex: Int

    @Environment(SpeechPlayer.self) private var speechPlayer

    var body: some View {
        // A Grid keeps the sentences in one column, even when the labels have different widths.
        Grid(alignment: .topLeading, horizontalSpacing: Theme.Spacing.small, verticalSpacing: Theme.Spacing.small) {
            ForEach(Array(examples.enumerated()), id: \.offset) { index, example in
                let speechID = "example-\(senseIndex)-\(index)"
                GridRow {
                    if let level = example.level {
                        ExampleLevelLabel(level: level)
                    } else {
                        Color.clear.frame(width: 0, height: 0)
                    }
                    Text(example.text)
                        .font(.system(.callout, design: .serif).italic())
                        .foregroundStyle(Theme.Colors.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                        .speechHighlight(isPlaying: speechPlayer.currentID == speechID)
                    IconButton(
                        systemImage: speechPlayer.currentID == speechID ? "stop.fill" : "speaker.wave.2",
                        label: speechPlayer.currentID == speechID ? "Stop" : "Listen to the example",
                        size: .compact
                    ) {
                        if speechPlayer.currentID == speechID {
                            speechPlayer.stop()
                        } else {
                            speechPlayer.speak(example.text, id: speechID)
                        }
                    }
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
