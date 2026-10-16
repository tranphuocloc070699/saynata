/**
 * Server-sent events parser, the same rules as the iOS SSEParser: bytes may arrive in any chunk size,
 * a line ends at \n (a \r before it is dropped), an empty line ends an event, comments and id/retry are ignored.
 */

export interface SseEvent {
  event: string;
  data: string;
}

export class SseParser {
  private readonly decoder = new TextDecoder();
  private buffer = "";
  private eventName = "";
  private dataLines: string[] = [];

  /** Feeds one chunk; returns the events it completed. A multi-byte character split between chunks is kept. */
  push(chunk: Uint8Array | string): SseEvent[] {
    this.buffer += typeof chunk === "string" ? chunk : this.decoder.decode(chunk, { stream: true });
    const events: SseEvent[] = [];
    let newline = this.buffer.indexOf("\n");
    while (newline !== -1) {
      const line = this.buffer.slice(0, newline).replace(/\r$/, "");
      this.buffer = this.buffer.slice(newline + 1);
      const event = this.line(line);
      if (event) events.push(event);
      newline = this.buffer.indexOf("\n");
    }
    return events;
  }

  private line(line: string): SseEvent | null {
    if (line === "") {
      if (this.dataLines.length === 0 && this.eventName === "") return null;
      const event = { event: this.eventName || "message", data: this.dataLines.join("\n") };
      this.eventName = "";
      this.dataLines = [];
      return event;
    }
    if (line.startsWith(":")) return null;
    const colon = line.indexOf(":");
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? "" : line.slice(colon + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    if (field === "event") this.eventName = value;
    else if (field === "data") this.dataLines.push(value);
    return null;
  }
}
