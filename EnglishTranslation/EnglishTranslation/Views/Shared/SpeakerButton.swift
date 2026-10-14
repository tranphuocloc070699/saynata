//
//  SpeakerButton.swift
//  EnglishTranslation
//

import SwiftUI

/// The Listen button for one `SpeechPart` (F3). Shows a speaker, or a stop square while its part plays.
/// Tapping it starts the part, or stops it when it is already playing. Built on `IconButton`.
struct SpeakerButton: View {
    let part: SpeechPart
    var size: IconButton.Size = .regular

    @Environment(\.speechPlayer) private var speech

    var body: some View {
        let isPlaying = speech.isPlaying(part)
        IconButton(
            systemImage: isPlaying ? "stop.fill" : "speaker.wave.2",
            label: isPlaying ? part.kind.stopLabel : part.kind.listenLabel,
            size: size
        ) {
            speech.toggle(part)
        }
    }
}

extension View {
    /// A soft accent background behind the text that is being read aloud.
    func speechHighlight(_ part: SpeechPart?) -> some View {
        modifier(SpeechHighlight(part: part))
    }
}

private struct SpeechHighlight: ViewModifier {
    let part: SpeechPart?
    @Environment(\.speechPlayer) private var speech

    private var isPlaying: Bool { part.map(speech.isPlaying) ?? false }

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: Theme.Spacing.small)
                    .fill(Theme.Colors.accent.opacity(isPlaying ? 0.12 : 0))
                    .padding(-Theme.Spacing.xSmall))
            .animation(.easeOut(duration: 0.15), value: isPlaying)
    }
}

#Preview {
    HStack {
        SpeakerButton(part: .sentence("Hello there."))
        SpeakerButton(part: .sentence("Hello there."), size: .compact)
    }
    .padding()
    .background(Theme.Colors.background)
}
