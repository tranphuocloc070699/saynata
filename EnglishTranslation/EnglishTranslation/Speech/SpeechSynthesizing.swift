//
//  SpeechSynthesizing.swift
//  EnglishTranslation
//

import AVFoundation

/// Something that can read text aloud. `SpeechPlayer` talks to this, so tests can use a fake
/// and never make a sound.
@MainActor
protocol SpeechSynthesizing: AnyObject {
    /// Called when an utterance ends, by finishing or by being cancelled. The token says which one.
    var onUtteranceEnded: ((_ token: Int) -> Void)? { get set }

    /// Starts reading `text`. `token` names this utterance in `onUtteranceEnded`.
    func speak(_ text: String, token: Int)

    /// Stops the current utterance at once, if there is one.
    func stop()
}

/// The real reader: `AVSpeechSynthesizer` with an en-US voice. It runs on the device, so it works offline.
@MainActor
final class AppleSpeechSynthesizer: NSObject, SpeechSynthesizing {
    var onUtteranceEnded: ((_ token: Int) -> Void)?

    private let synthesizer = AVSpeechSynthesizer()
    private var activeToken: Int?

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func speak(_ text: String, token: Int) {
        prepareAudioSession()
        let utterance = TokenUtterance(string: text, token: token)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        activeToken = token
        synthesizer.speak(utterance)
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
    }

    /// `.playback` + `.spokenAudio` makes speech play even when the silent switch is on.
    private func prepareAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio)
        try? session.setActive(true)
    }
}

extension AppleSpeechSynthesizer: AVSpeechSynthesizerDelegate {
    // AVFoundation does not mark these as main-actor, so we read the token here
    // and hop to the main actor to tell the player.
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        utteranceEnded(utterance)
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        utteranceEnded(utterance)
    }

    private nonisolated func utteranceEnded(_ utterance: AVSpeechUtterance) {
        guard let token = (utterance as? TokenUtterance)?.token else { return }
        Task { @MainActor in
            if self.activeToken == token {
                self.activeToken = nil
                self.deactivateAudioSession()
            }
            self.onUtteranceEnded?(token)
        }
    }

    /// Gives audio back to music and other apps after speech ends.
    private func deactivateAudioSession() {
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

/// An utterance that remembers its token, so a late callback can be matched to the right one.
private final class TokenUtterance: AVSpeechUtterance, @unchecked Sendable {
    let token: Int

    init(string: String, token: Int) {
        self.token = token
        super.init(string: string)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not used")
    }
}
