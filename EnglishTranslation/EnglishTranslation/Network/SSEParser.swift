//
//  SSEParser.swift
//  EnglishTranslation
//

import Foundation

/// One server-sent event: a name and its data text.
struct SSEEvent: Equatable {
    /// The `event:` line. "message" when the server sent none.
    let name: String
    /// The `data:` lines. If there are several, they are joined with a newline.
    let data: String
}

/// Reads a server-sent events stream (`text/event-stream`) in pieces of any size.
///
/// The network gives us bytes in chunks that can end in the middle of a line, or even in the middle of
/// one character. So `feed` keeps the unfinished end and waits for the next chunk. An empty line ends
/// an event. Lines that start with ":" are comments and are ignored.
struct SSEParser {
    /// Bytes of a line that has no line break yet.
    private var pendingBytes: [UInt8] = []
    private var eventName: String?
    private var dataLines: [String] = []

    /// Adds the next chunk of the stream. Returns the events that are complete now (maybe none).
    mutating func feed(_ chunk: Data) -> [SSEEvent] {
        var events: [SSEEvent] = []
        for byte in chunk {
            guard byte == UInt8(ascii: "\n") else {
                pendingBytes.append(byte)
                continue
            }
            // A full line. Only now is it safe to turn the bytes into text.
            var line = String(decoding: pendingBytes, as: UTF8.self)
            pendingBytes.removeAll(keepingCapacity: true)
            if line.hasSuffix("\r") { line.removeLast() }
            if let event = readLine(line) {
                events.append(event)
            }
        }
        return events
    }

    /// Handles one complete line. Returns an event when the line is the empty line that ends one.
    private mutating func readLine(_ line: String) -> SSEEvent? {
        if line.isEmpty {
            defer {
                eventName = nil
                dataLines = []
            }
            // An empty line with no data is only a separator: nothing to send.
            guard !dataLines.isEmpty else { return nil }
            return SSEEvent(name: eventName ?? "message", data: dataLines.joined(separator: "\n"))
        }
        if line.hasPrefix(":") { return nil }

        let name: String
        var value: String
        if let colon = line.firstIndex(of: ":") {
            name = String(line[..<colon])
            value = String(line[line.index(after: colon)...])
            // The spec allows one space after the colon.
            if value.hasPrefix(" ") { value.removeFirst() }
        } else {
            name = line
            value = ""
        }

        switch name {
        case "event": eventName = value
        case "data": dataLines.append(value)
        default: break // id, retry: not used here
        }
        return nil
    }
}
