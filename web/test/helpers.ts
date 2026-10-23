/** A fake fetch that answers from a list of handlers, one per call, and records the requests. */
export interface Call {
  url: string;
  init: RequestInit;
}

export type Handler = (call: Call) => Response | Promise<Response>;

export function fakeFetch(...handlers: Handler[]) {
  const calls: Call[] = [];
  const fetchImpl = async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const call = { url: String(input), init };
    calls.push(call);
    const handler = handlers[calls.length - 1];
    if (!handler) throw new Error(`unexpected request ${calls.length}: ${call.url}`);
    return handler(call);
  };
  return { fetchImpl: fetchImpl as typeof fetch, calls };
}

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...headers } });
}

/** A text/event-stream response that sends each chunk as it is (chunks may cut lines anywhere). */
export function sse(...chunks: string[]): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    },
  });
  return new Response(body, { status: 200, headers: { "Content-Type": "text/event-stream" } });
}

export function event(name: string, data: unknown): string {
  return `event:${name}\ndata:${JSON.stringify(data)}\n\n`;
}

export function header(call: Call, name: string): string | undefined {
  return (call.init.headers as Record<string, string> | undefined)?.[name];
}

export const sentenceResponse = {
  text: "reluctant to approve the plan",
  targetLang: "vi",
  explanation: {
    meaning: "Not wanting to say yes to the plan.",
    words: [{ word: "reluctant", partOfSpeech: "adjective", meaning: "not wanting to do something" }],
    translation: "miễn cưỡng chấp thuận kế hoạch",
  },
  cached: false,
};

export const wordResponse = {
  word: "reluctant",
  entry: {
    senses: [
      {
        pos: "adjective",
        cefr: "B2",
        simpleDefinition: "Not wanting to do something.",
        vietnamese: "miễn cưỡng",
        examples: [{ level: "easy", text: "She was reluctant to leave." }],
        usage: [],
      },
    ],
  },
  cached: true,
  ipa: "/rɪˈlʌktənt/",
};
