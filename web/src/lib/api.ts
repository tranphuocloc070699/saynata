/**
 * The backend client, copied from the extension (extension/src/shared/api.ts) and given suggest().
 * The web app calls it with an empty base URL: /api is on the same origin (a Next.js rewrite in dev,
 * Caddy in production), so the backend needs no CORS setup. Same rules as the iOS BackendClient: Bearer when signed in, one refresh and one retry on a 401 with a token,
 * the stream first for sentences and the old endpoint once as a fallback (doc-4, doc-17).
 */
import type { LanguageCode } from "./languages";
import { SseParser } from "./sse";

// Response shapes (backend WordResponse, TranslationResponse).

export interface Example {
  level: string;
  text: string;
}

export interface Usage {
  kind: string;
  pattern: string;
  example: string;
}

export interface Sense {
  pos: string;
  cefr: string;
  simpleDefinition: string;
  vietnamese: string;
  examples: Example[];
  usage: Usage[];
  imageUrl?: string;
}

export interface WordResponse {
  word: string;
  entry: { senses: Sense[] };
  cached: boolean;
  ipa: string | null;
}

export interface SentenceWord {
  word: string;
  partOfSpeech: string;
  meaning: string;
}

export interface Explanation {
  meaning: string;
  words: SentenceWord[];
  translation: string;
}

export interface TranslationResponse {
  text: string;
  targetLang: LanguageCode;
  explanation: Explanation;
  cached: boolean;
}

/** GET /api/suggest: up to 8 bundled or cached words and phrases that start with the prefix. */
export interface Suggestion {
  text: string;
  kind: "word" | "phrase";
}

export interface SuggestResponse {
  query: string;
  suggestions: Suggestion[];
}

// Errors.

export type ApiErrorKind =
  | "signInRequired"
  | "rateLimited"
  | "dailyLimit"
  | "notEnglish"
  | "badRequest"
  | "server"
  | "network"
  | "streamEnded";

export class ApiError extends Error {
  constructor(
    readonly kind: ApiErrorKind,
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }

  /** A 4xx is the server's final answer: the fallback endpoint would say the same. */
  get isFinalAnswer(): boolean {
    return this.status !== undefined && this.status >= 400 && this.status < 500;
  }
}

export const MESSAGES = {
  signIn: "Sign in to get a new explanation.",
  network: "Can't reach Saynata. Check your connection and try again.",
  generic: "Something went wrong. Try again.",
  streamEnded: "The answer stopped before the end. Try again.",
} as const;

interface ErrorBody {
  code?: string;
  detail?: string;
  message?: string;
}

/** Turns an HTTP status and a ProblemDetail body (or a stream `error` event) into an ApiError. */
export function errorFrom(status: number, body: ErrorBody | null, retryAfter?: string | null): ApiError {
  const serverText = body?.detail ?? body?.message;
  switch (body?.code) {
    case "sign_in_required":
      return new ApiError("signInRequired", MESSAGES.signIn, status);
    case "rate_limited":
      return new ApiError("rateLimited", rateLimitMessage(retryAfter), status);
    case "daily_limit_reached":
      return new ApiError("dailyLimit", serverText ?? MESSAGES.generic, status);
  }
  if (status === 401) return new ApiError("signInRequired", MESSAGES.signIn, status);
  if (status === 429) return new ApiError("rateLimited", rateLimitMessage(retryAfter), status);
  if (status === 422) return new ApiError("notEnglish", serverText ?? "This doesn't look like English.", status);
  if (status >= 400 && status < 500) return new ApiError("badRequest", serverText ?? MESSAGES.generic, status);
  return new ApiError("server", serverText ?? MESSAGES.generic, status);
}

/** "Too many requests. Try again in N minutes." from Retry-After in seconds, rounded up to whole minutes. */
export function rateLimitMessage(retryAfter?: string | null): string {
  const seconds = retryAfter ? Number(retryAfter) : NaN;
  if (!Number.isFinite(seconds) || seconds <= 0) return "Too many requests. Try again in a little while.";
  const minutes = Math.max(1, Math.ceil(seconds / 60));
  return `Too many requests. Try again in ${minutes} ${minutes === 1 ? "minute" : "minutes"}.`;
}

async function errorFromResponse(response: Response): Promise<ApiError> {
  let body: ErrorBody | null = null;
  try {
    body = (await response.json()) as ErrorBody;
  } catch {
    body = null;
  }
  return errorFrom(response.status, body, response.headers.get("Retry-After"));
}

// Tokens.

/** Where the access token comes from. A guest has none. */
export interface TokenSource {
  accessToken(): Promise<string | null>;
  /** A new access token after `old` was refused, or null when the user must sign in again. */
  refreshedAccessToken(old: string): Promise<string | null>;
}

export const guestTokens: TokenSource = {
  accessToken: async () => null,
  refreshedAccessToken: async () => null,
};

// Client.

export interface StreamHandlers {
  /** New characters of the meaning while Claude writes it (`meaning-delta`). */
  onMeaningDelta?(text: string): void;
  /** The full meaning; it always wins over the pieces (`meaning`). */
  onMeaning?(meaning: string): void;
}

type Fetch = typeof fetch;

export class SaynataApi {
  constructor(
    private readonly baseUrl: string,
    private readonly tokens: TokenSource = guestTokens,
    private readonly fetchImpl: Fetch = (input, init) => fetch(input, init),
  ) {}

  /** Never reaches Claude, so a guest may call it on every key (doc-5). */
  async suggest(prefix: string, targetLang: LanguageCode, signal?: AbortSignal): Promise<Suggestion[]> {
    const query = new URLSearchParams({ q: prefix, lang: targetLang });
    const response = await this.send(`${this.baseUrl}/api/suggest?${query}`, {
      headers: this.headers(null, false),
      signal,
    });
    if (!response.ok) throw await errorFromResponse(response);
    return ((await response.json()) as SuggestResponse).suggestions;
  }

  async lookUpWord(word: string, signal?: AbortSignal): Promise<WordResponse> {
    const url = `${this.baseUrl}/api/words/${encodeURIComponent(word)}`;
    const response = await this.withAuth((token) =>
      this.send(url, { headers: this.headers(token, false), signal }),
    );
    if (!response.ok) throw await errorFromResponse(response);
    return (await response.json()) as WordResponse;
  }

  /** The stream first; if it fails with anything but a final 4xx answer or a cancel, the old endpoint once. */
  async explainSentence(
    text: string,
    targetLang: LanguageCode,
    handlers: StreamHandlers = {},
    signal?: AbortSignal,
  ): Promise<TranslationResponse> {
    try {
      return await this.withAuthRetryOnError((token) => this.stream(text, targetLang, token, handlers, signal));
    } catch (error) {
      if (signal?.aborted || (error instanceof ApiError && error.isFinalAnswer)) throw error;
      return this.explainOnce(text, targetLang, signal);
    }
  }

  async explainOnce(text: string, targetLang: LanguageCode, signal?: AbortSignal): Promise<TranslationResponse> {
    const response = await this.withAuth((token) =>
      this.send(`${this.baseUrl}/api/translations`, {
        method: "POST",
        headers: this.headers(token, true),
        body: JSON.stringify({ text, targetLang }),
        signal,
      }),
    );
    if (!response.ok) throw await errorFromResponse(response);
    return (await response.json()) as TranslationResponse;
  }

  private async stream(
    text: string,
    targetLang: LanguageCode,
    token: string | null,
    handlers: StreamHandlers,
    signal?: AbortSignal,
  ): Promise<TranslationResponse> {
    const headers = { ...this.headers(token, true), Accept: "text/event-stream" };
    const response = await this.send(`${this.baseUrl}/api/translations/stream`, {
      method: "POST",
      headers,
      body: JSON.stringify({ text, targetLang }),
      signal,
    });
    if (!response.ok) throw await errorFromResponse(response);
    if (!response.body) throw new ApiError("streamEnded", MESSAGES.streamEnded);

    const parser = new SseParser();
    const reader = response.body.getReader();
    for (;;) {
      const { value, done } = await reader.read().catch(() => {
        throw new ApiError("network", MESSAGES.network);
      });
      if (done) break;
      for (const event of parser.push(value)) {
        const data = parseJson(event.data);
        if (event.event === "meaning-delta") {
          if (typeof data?.text === "string") handlers.onMeaningDelta?.(data.text);
        } else if (event.event === "meaning") {
          if (typeof data?.meaning === "string") handlers.onMeaning?.(data.meaning);
        } else if (event.event === "done") {
          if (!data) throw new ApiError("server", MESSAGES.generic);
          await reader.cancel().catch(() => undefined);
          return data as unknown as TranslationResponse;
        } else if (event.event === "error") {
          const status = typeof data?.status === "number" ? data.status : 502;
          throw errorFrom(status, data as ErrorBody | null);
        }
      }
    }
    throw new ApiError("streamEnded", MESSAGES.streamEnded);
  }

  private headers(token: string | null, json: boolean): Record<string, string> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (json) headers["Content-Type"] = "application/json";
    if (token) headers.Authorization = `Bearer ${token}`;
    return headers;
  }

  private async send(url: string, init: RequestInit): Promise<Response> {
    try {
      return await this.fetchImpl(url, init);
    } catch (error) {
      if (init.signal?.aborted) throw error;
      throw new ApiError("network", MESSAGES.network);
    }
  }

  /** Runs the request; a 401 with a token refreshes once and runs it again. A guest's 401 goes to the caller. */
  private async withAuth(run: (token: string | null) => Promise<Response>): Promise<Response> {
    const token = await this.tokens.accessToken();
    const response = await run(token);
    if (response.status !== 401 || !token) return response;
    const fresh = await this.tokens.refreshedAccessToken(token);
    if (!fresh) throw new ApiError("signInRequired", MESSAGES.signIn, 401);
    return run(fresh);
  }

  /** The same for a call that reports a 401 by throwing (the stream's `error` event). */
  private async withAuthRetryOnError<T>(run: (token: string | null) => Promise<T>): Promise<T> {
    const token = await this.tokens.accessToken();
    try {
      return await run(token);
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401 || !token) throw error;
      const fresh = await this.tokens.refreshedAccessToken(token);
      if (!fresh) throw new ApiError("signInRequired", MESSAGES.signIn, 401);
      return run(fresh);
    }
  }
}

function parseJson(text: string): Record<string, unknown> | null {
  try {
    const value: unknown = JSON.parse(text);
    return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
