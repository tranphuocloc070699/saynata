//
//  SpeechPlayer.swift
//  EnglishTranslation
//

import Observation
import SwiftUI

/// Reads one part of the screen aloud at a time (F3 Listen, TASK-7).
/// Created once in `EnglishTranslationApp` and shared with `.environment`.
/// A view asks `currentID == part.id` to know if its part is playing.
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

    /// A player that makes no sound. It is the default in previews, where nobody injects a real one.
    static func silent() -> SpeechPlayer {
        SpeechPlayer(synthesizer: SilentSynthesizer())
    }

    /// Reads `text`. If something else is playing, it stops first.
    func speak(text: String, id: String) {
        synthesizer.stop()
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

    /// The speaker button: stops the part if it is playing, otherwise reads it.
    func toggle(_ part: SpeechPart) {
        if currentID == part.id {
            stop()
        } else {
            speak(text: part.text, id: part.id)
        }
    }

    func isPlaying(_ part: SpeechPart) -> Bool {
        currentID == part.id
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

extension EnvironmentValues {
    /// The shared player. The default is silent, so previews and tests need no setup.
    @Entry var speechPlayer = SpeechPlayer.silent()
}
