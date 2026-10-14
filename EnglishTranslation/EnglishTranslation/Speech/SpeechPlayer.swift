//
//  SpeechPlayer.swift
//  EnglishTranslation
//

import Observation
import SwiftUI

/// Reads one part of the screen aloud at a time (F3 Listen, TASK-7).
/// Created once in `EnglishTranslationApp` and shared with `.environment`.
/// A view compares `currentID` with its own id to know if its text is playing.
@Observable
@MainActor
final class SpeechPlayer {
    /// The id of the part being read now, or nil when nothing plays.
    private(set) var currentID: String?

    private let synthesizer: any SpeechSynthesizing
    /// Counts utterances. A callback with an old token is for an utterance we already replaced.
    private var latestToken = 0

    init(synthesizer: (any SpeechSynthesizing)? = nil) {
        let synthesizer = synthesizer ?? AppleSpeechSynthesizer()
        self.synthesizer = synthesizer
        synthesizer.onUtteranceEnded = { [weak self] token in
            self?.utteranceEnded(token: token)
        }
    }

    /// A player that makes no sound. Previews inject this instead of the real audio player.
    static func silent() -> SpeechPlayer {
        SpeechPlayer(synthesizer: SilentSynthesizer())
    }

    /// Reads `text`. If something else is playing, it stops first. Empty text is ignored.
    func speak(_ text: String, id: String) {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        if currentID != nil {
            synthesizer.stop()
        }
        latestToken += 1
        currentID = id
        synthesizer.speak(text, token: latestToken)
    }

    /// Stops the current part. Does nothing when nothing plays.
    func stop() {
        guard currentID != nil else { return }
        // Moving the token on makes the "cancelled" callback of this utterance harmless.
        latestToken += 1
        currentID = nil
        synthesizer.stop()
    }

    private func utteranceEnded(token: Int) {
        guard token == latestToken else { return }
        currentID = nil
    }
}

/// Does nothing. Used by `SpeechPlayer.silent()`.
@MainActor
private final class SilentSynthesizer: SpeechSynthesizing {
    var onUtteranceEnded: ((_ token: Int) -> Void)?
    func speak(_ text: String, token: Int) {}
    func stop() {}
}

extension View {
    /// Adds a soft accent background while this text is being read aloud.
    func speechHighlight(isPlaying: Bool) -> some View {
        background(
            RoundedRectangle(cornerRadius: Theme.Radius.block)
                .fill(Theme.Colors.accent.opacity(isPlaying ? 0.12 : 0))
                .padding(-Theme.Spacing.xSmall)
        )
        .animation(.easeOut(duration: 0.15), value: isPlaying)
    }
}
