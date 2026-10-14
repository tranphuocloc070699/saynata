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
        player.speak(text: "Hello", id: "a")
        #expect(player.currentID == "a")
        #expect(fake.spoken.map(\.text) == ["Hello"])
    }

    @Test func speakingANewIDReplacesTheOldOne() {
        let (player, fake) = makePlayer()
        player.speak(text: "One", id: "a")
        player.speak(text: "Two", id: "b")
        #expect(player.currentID == "b")
        #expect(fake.stopCount >= 1)
        #expect(fake.spoken.map(\.text) == ["One", "Two"])
    }

    @Test func stopClearsCurrentID() {
        let (player, fake) = makePlayer()
        player.speak(text: "One", id: "a")
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
        player.speak(text: "One", id: "a")
        fake.end(token: fake.spoken[0].token)
        #expect(player.currentID == nil)
    }

    @Test func lateCallbackOfAReplacedUtteranceIsIgnored() {
        let (player, fake) = makePlayer()
        player.speak(text: "One", id: "a")
        let oldToken = fake.spoken[0].token
        player.speak(text: "Two", id: "b")
        // The first utterance reports "cancelled" after the second has started.
        fake.end(token: oldToken)
        #expect(player.currentID == "b")
        fake.end(token: fake.spoken[1].token)
        #expect(player.currentID == nil)
    }

    @Test func lateCallbackAfterStopDoesNotTouchTheNextPart() {
        let (player, fake) = makePlayer()
        player.speak(text: "One", id: "a")
        let oldToken = fake.spoken[0].token
        player.stop()
        player.speak(text: "Two", id: "b")
        fake.end(token: oldToken)
        #expect(player.currentID == "b")
    }

    @Test func toggleStartsThenStops() {
        let (player, _) = makePlayer()
        let part = SpeechPart.sentence("Hello there.")
        player.toggle(part)
        #expect(player.isPlaying(part))
        player.toggle(part)
        #expect(!player.isPlaying(part))
    }

    @Test func togglingAnotherPartMovesPlayback() {
        let (player, _) = makePlayer()
        let first = SpeechPart.sentence("One.")
        let second = SpeechPart.sentence("Two.")
        player.toggle(first)
        player.toggle(second)
        #expect(!player.isPlaying(first))
        #expect(player.isPlaying(second))
    }

    // MARK: - Text chosen per part

    @Test func wordIsReadAsTyped() {
        #expect(SpeechPart.word(of: .ran).text == "ran")
        #expect(SpeechPart.word(of: .run).text == "run")
    }

    @Test func explanationIsTheSenseSimpleDefinition() {
        let sense = WordEntry.apple.senses[0]
        let part = SpeechPart.explanation(of: sense, senseIndex: 0, in: .apple)
        #expect(part.text == sense.simpleDefinition)
        #expect(part.kind == .explanation)
    }

    @Test func exampleIsTheExampleTextOnly() {
        let example = WordEntry.apple.senses[0].examples[0]
        let part = SpeechPart.example(example, exampleIndex: 0, senseIndex: 0, in: .apple)
        #expect(part.text == example.text)
        #expect(part.kind == .example)
    }

    @Test func sentenceIsReadAsWritten() {
        #expect(SpeechPart.sentence("The mist blurred the edges.").text == "The mist blurred the edges.")
    }

    @Test func everyPartOfAWordCardHasItsOwnID() {
        let entry = WordEntry.apple
        var ids = [SpeechPart.word(of: entry).id]
        for (senseIndex, sense) in entry.senses.enumerated() {
            ids.append(SpeechPart.explanation(of: sense, senseIndex: senseIndex, in: entry).id)
            for (exampleIndex, example) in sense.examples.enumerated() {
                ids.append(SpeechPart.example(example, exampleIndex: exampleIndex, senseIndex: senseIndex, in: entry).id)
            }
        }
        #expect(Set(ids).count == ids.count)
    }

    @Test func labelsNameThePartAndChangeWhilePlaying() {
        #expect(SpeechPart.Kind.word.listenLabel == "Listen to the word")
        #expect(SpeechPart.Kind.explanation.listenLabel == "Listen to the explanation")
        #expect(SpeechPart.Kind.example.listenLabel == "Listen to the example")
        for kind in [SpeechPart.Kind.word, .explanation, .example, .sentence] {
            #expect(kind.stopLabel != kind.listenLabel)
        }
    }
}
