import { describe, expect, it } from "vitest";
import { SseParser } from "../src/lib/sse";

describe("SseParser", () => {
  it("reads named events with JSON data", () => {
    const events = new SseParser().push('event:meaning\ndata:{"meaning":"Hi"}\n\nevent:done\ndata:{}\n\n');
    expect(events).toEqual([
      { event: "meaning", data: '{"meaning":"Hi"}' },
      { event: "done", data: "{}" },
    ]);
  });

  it("joins events split at every position", () => {
    const stream = 'event: meaning-delta\ndata: {"text":"Not "}\n\nevent:meaning-delta\r\ndata:{"text":"want"}\r\n\r\n';
    for (let cut = 0; cut <= stream.length; cut++) {
      const parser = new SseParser();
      const events = [...parser.push(stream.slice(0, cut)), ...parser.push(stream.slice(cut))];
      expect(events.map((e) => e.data)).toEqual(['{"text":"Not "}', '{"text":"want"}']);
      expect(events.every((e) => e.event === "meaning-delta")).toBe(true);
    }
  });

  it("keeps a multi-byte character split between byte chunks", () => {
    const bytes = new TextEncoder().encode('event:meaning\ndata:{"meaning":"miễn"}\n\n');
    for (let cut = 0; cut <= bytes.length; cut++) {
      const parser = new SseParser();
      const events = [...parser.push(bytes.slice(0, cut)), ...parser.push(bytes.slice(cut))];
      expect(events).toEqual([{ event: "meaning", data: '{"meaning":"miễn"}' }]);
    }
  });

  it("ignores comments, id and retry, and joins data lines", () => {
    const events = new SseParser().push(": keep-alive\nid: 7\nretry: 1000\ndata: a\ndata: b\n\n");
    expect(events).toEqual([{ event: "message", data: "a\nb" }]);
  });

  it("gives nothing until the empty line arrives", () => {
    expect(new SseParser().push("event:done\ndata:{}\n")).toEqual([]);
  });
});
