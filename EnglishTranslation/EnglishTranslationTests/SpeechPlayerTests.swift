//
//  SpeechPlayerTests.swift
//  EnglishTranslationTests
//

import Testing
@testable import EnglishTranslation

/// A fake reader that records what it was asked to do and makes no sound.
@MainActor
private final class FakeSynthesizer: SpeechSynthesizing {
    var onUtteranceEnded: ((_ token: Int) -> Void)?
    private(set) var spoken: [(text: String, token: Int)] = []
    private(set) var stopCount = 0

    func speak(_ text: String, token: Int) {
        spoken.append((text, token))
    }

    func stop() {
        stopCount += 1
    }

    /// Pretends an utterance finished or was cancelled.
    func end(token: Int) {
        onUtteranceEnded?(token)
    }
}

@MainActor
struct SpeechPlayerTests {

    private func makePlayer() -> (SpeechPlayer, FakeSynthesizer) {
        let fake = FakeSynthesizer()
        return (SpeechPlayer(synthesizer: fake), fake)
    }

    // MARK: - Player

    @Test func speakSetsCurrentIDAndReadsTheText() {
        let (player, fake) = makePlayer()
        player.speak("Hello", id: "a")
        #expect(player.currentID == "a")
        #expect(fake.spoken.map(\.text) == ["Hello"])
    }

    @Test func speakingANewIDReplacesTheOldOne() {
        let (player, fake) = makePlayer()
        player.speak("One", id: "a")
        player.speak("Two", id: "b")
        #expect(player.currentID == "b")
        #expect(fake.stopCount == 1)
        #expect(fake.spoken.map(\.text) == ["One", "Two"])
    }

    @Test func stopClearsCurrentID() {
        let (player, fake) = makePlayer()
        player.speak("One", id: "a")
        let stopsBefore = fake.stopCount
        player.stop()
        #expect(player.currentID == nil)
        #expect(fake.stopCount == stopsBefore + 1)
    }

    @Test func stopWhenNothingPlaysDoesNothing() {
        let (player, fake) = makePlayer()
        player.stop()
        #expect(player.currentID == nil)
        #expect(fake.stopCount == 0)
    }

    @Test func finishingClearsCurrentID() {
        let (player, fake) = makePlayer()
        player.speak("One", id: "a")
        fake.end(token: fake.spoken[0].token)
        #expect(player.currentID == nil)
    }

    @Test func lateCallbackOfAReplacedUtteranceIsIgnored() {
        let (player, fake) = makePlayer()
        player.speak("One", id: "a")
        let oldToken = fake.spoken[0].token
        player.speak("Two", id: "b")
        // The first utterance reports "cancelled" after the second has started.
        fake.end(token: oldToken)
        #expect(player.currentID == "b")
        fake.end(token: fake.spoken[1].token)
        #expect(player.currentID == nil)
    }

    @Test func lateCallbackAfterStopDoesNotTouchTheNextPart() {
        let (player, fake) = makePlayer()
        player.speak("One", id: "a")
        let oldToken = fake.spoken[0].token
        player.stop()
        player.speak("Two", id: "b")
        fake.end(token: oldToken)
        #expect(player.currentID == "b")
    }

    @Test func emptyTextDoesNothing() {
        let (player, fake) = makePlayer()
        player.speak("Keep playing", id: "current")
        let stopsBefore = fake.stopCount
        player.speak("", id: "empty")
        player.speak("   \n", id: "whitespace")
        #expect(player.currentID == "current")
        #expect(fake.spoken.map(\.text) == ["Keep playing"])
        #expect(fake.stopCount == stopsBefore)
    }

    // MARK: - Leaving a screen (TASK-46)

    @Test func leavingAScreenCallsStopOnTheSynthesizerAndClearsCurrentID() {
        let (player, fake) = makePlayer()
        player.speak("One", id: "a")
        let stopsBefore = fake.stopCount
        // The views call stop() from onChange, onDisappear and when the app goes to the background.
        player.stop()
        #expect(player.currentID == nil)
        #expect(fake.stopCount == stopsBefore + 1)
    }

    @Test func resultKeyChangesWhenTheShownResultChanges() {
        #expect(TranslationState.idle.resultKey != TranslationState.loading.resultKey)
        #expect(TranslationState.loadedWord(.apple).resultKey == TranslationState.loadedWord(.apple).resultKey)
        #expect(TranslationState.loadedWord(.apple).resultKey != TranslationState.loadedWord(.depend).resultKey)
    }
}
