//
//  SSEParserTests.swift
//  EnglishTranslationTests
//

import Foundation
import Testing
@testable import EnglishTranslation

@MainActor
struct SSEParserTests {
    private func feed(_ chunks: [String]) -> [SSEEvent] {
        var parser = SSEParser()
        return chunks.flatMap { parser.feed(Data($0.utf8)) }
    }

    @Test func readsOneEvent() {
        let events = feed(["event:meaning\ndata:{\"meaning\":\"Hi.\"}\n\n"])
        #expect(events == [SSEEvent(name: "meaning", data: "{\"meaning\":\"Hi.\"}")])
    }

    @Test func readsSeveralEventsInOneChunk() {
        let events = feed(["event:meaning\ndata:1\n\nevent:done\ndata:2\n\n"])
        #expect(events.map(\.name) == ["meaning", "done"])
        #expect(events.map(\.data) == ["1", "2"])
    }

    @Test func eventSplitBetweenChunksIsJoined() {
        let events = feed(["event:mean", "ing\ndata:{\"mea", "ning\":\"Hi.\"}\n", "\n"])
        #expect(events == [SSEEvent(name: "meaning", data: "{\"meaning\":\"Hi.\"}")])
    }

    @Test func nothingIsReturnedUntilTheEmptyLine() {
        var parser = SSEParser()
        #expect(parser.feed(Data("event:done\ndata:x\n".utf8)).isEmpty)
        #expect(parser.feed(Data("\n".utf8)) == [SSEEvent(name: "done", data: "x")])
    }

    @Test func splitInsideAMultiByteCharacterStillWorks() {
        // "ấ" is 3 bytes in UTF-8. Cut the stream in the middle of it.
        let bytes = Array("event:meaning\ndata:Anh ấy\n\n".utf8)
        let cut = bytes.firstIndex(of: 0xE1)! + 1
        var parser = SSEParser()
        var events = parser.feed(Data(bytes[..<cut]))
        events += parser.feed(Data(bytes[cut...]))
        #expect(events == [SSEEvent(name: "meaning", data: "Anh ấy")])
    }

    @Test func oneByteAtATime() {
        var parser = SSEParser()
        var events: [SSEEvent] = []
        for byte in Array("event:done\ndata:{\"a\":\"Việt\"}\n\n".utf8) {
            events += parser.feed(Data([byte]))
        }
        #expect(events == [SSEEvent(name: "done", data: "{\"a\":\"Việt\"}")])
    }

    @Test func windowsLineEndingsAreAccepted() {
        let events = feed(["event:done\r\ndata:x\r\n\r\n"])
        #expect(events == [SSEEvent(name: "done", data: "x")])
    }

    @Test func spaceAfterTheColonIsOptional() {
        let events = feed(["event: done\ndata: x\n\n"])
        #expect(events == [SSEEvent(name: "done", data: "x")])
    }

    @Test func severalDataLinesAreJoinedWithANewline() {
        let events = feed(["data:one\ndata:two\n\n"])
        #expect(events == [SSEEvent(name: "message", data: "one\ntwo")])
    }

    @Test func commentsAndUnknownFieldsAreIgnored() {
        let events = feed([": keep-alive\nid:7\nretry:100\nevent:done\ndata:x\n\n"])
        #expect(events == [SSEEvent(name: "done", data: "x")])
    }

    @Test func extraEmptyLinesMakeNoEvents() {
        #expect(feed(["\n\n\n"]).isEmpty)
    }

    @Test func eventNameDoesNotLeakIntoTheNextEvent() {
        let events = feed(["event:meaning\ndata:1\n\ndata:2\n\n"])
        #expect(events.map(\.name) == ["meaning", "message"])
    }
}
