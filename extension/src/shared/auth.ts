/**
 * Email-code sign-in (doc-17), the same calls and rules as the iOS AuthClient and AccountStore.
 * The session lives in chrome.storage.local (this browser only, never synced) and only the service worker
 * changes it, so there is one refresh at a time: the server accepts each refresh token once, and a second
 * use revokes every session of the user.
 */
import { ApiError, MESSAGES, rateLimitMessage, type TokenSource } from "./api";
import type { KeyValueArea } from "./settings";

export interface User {
  id: number | string;
  email: string;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export const SESSION_KEY = "session";

export type AuthErrorKind = "invalidEmail" | "invalidCode" | "invalidRefreshToken" | "rateLimited" | "network" | "server";

export class AuthError extends Error {
  constructor(
    readonly kind: AuthErrorKind,
    message: string,
  ) {
    super(message);
    this.name = "AuthError";
  }
}

type Fetch = typeof fetch;

/** The four /api/auth calls. */
export class AuthClient {
  constructor(
    private readonly baseUrl: string,
    private readonly fetchImpl: Fetch = (input, init) => fetch(input, init),
  ) {}

  /** Asks the server to email a 6-digit code. 204 for any known or unknown email. */
  async requestCode(email: string): Promise<void> {
    await this.post("/api/auth/email-code", { email });
  }

  async verify(email: string, code: string): Promise<Session> {
    return toSession(await this.post("/api/auth/verify", { email, code }));
  }

  async refresh(refreshToken: string): Promise<Session> {
    return toSession(await this.post("/api/auth/refresh", { refreshToken }));
  }

  /** Always 204 on the server; a failure here does not matter (the device is signed out first). */
  async signOut(refreshToken: string): Promise<void> {
    await this.post("/api/auth/sign-out", { refreshToken });
  }

  private async post(path: string, body: unknown): Promise<unknown> {
    let response: Response;
    try {
      response = await this.fetchImpl(`${this.baseUrl}${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(body),
      });
    } catch {
      throw new AuthError("network", MESSAGES.network);
    }
    if (response.status === 204) return null;
    const json = (await response.json().catch(() => null)) as { code?: string; detail?: string } | null;
    if (response.ok) return json;
    throw authError(response.status, json, response.headers.get("Retry-After"));
  }
}

export function authError(status: number, body: { code?: string; detail?: string } | null, retryAfter?: string | null): AuthError {
  switch (body?.code) {
    case "invalid_email":
      return new AuthError("invalidEmail", body.detail ?? "Enter a valid email address.");
    case "invalid_code":
      return new AuthError("invalidCode", "That code is wrong or has expired.");
    case "invalid_refresh_token":
      return new AuthError("invalidRefreshToken", "Your session has ended. Sign in again.");
  }
  if (status === 429) return new AuthError("rateLimited", rateLimitMessage(retryAfter));
  if (status === 400) return new AuthError("invalidEmail", body?.detail ?? "Enter a valid email address.");
  if (status === 401) return new AuthError("invalidCode", "That code is wrong or has expired.");
  return new AuthError("server", body?.detail ? `${body.detail} (status ${status})` : `The server answered with status ${status}.`);
}

function toSession(value: unknown): Session {
  const body = value as Partial<Session> | null;
  if (!body?.accessToken || !body.refreshToken || !body.user) throw new AuthError("server", MESSAGES.generic);
  return { accessToken: body.accessToken, refreshToken: body.refreshToken, user: body.user };
}

/** The signed-in user's session, and the TokenSource the API client uses. */
export class AuthStore implements TokenSource {
  private refreshing: Promise<string | null> | null = null;

  constructor(
    private readonly area: KeyValueArea & { remove(keys: string): Promise<void> },
    private readonly client: AuthClient,
  ) {}

  async session(): Promise<Session | null> {
    const stored = (await this.area.get([SESSION_KEY]))[SESSION_KEY] as Session | undefined;
    return stored?.accessToken && stored.refreshToken ? stored : null;
  }

  async accessToken(): Promise<string | null> {
    return (await this.session())?.accessToken ?? null;
  }

  async requestCode(email: string): Promise<void> {
    await this.client.requestCode(email.trim());
  }

  async signIn(email: string, code: string): Promise<User> {
    const session = await this.client.verify(email.trim(), code.trim());
    await this.area.set({ [SESSION_KEY]: session });
    return session.user;
  }

  /** Clears this browser first, then tells the server; a failed call is ignored. */
  async signOut(): Promise<void> {
    const session = await this.session();
    await this.area.remove(SESSION_KEY);
    if (session) await this.client.signOut(session.refreshToken).catch(() => undefined);
  }

  /**
   * A new access token after `old` was refused. Calls that fail together share one refresh. If the session
   * already has another token (someone refreshed first), that one is returned. A refused refresh signs out
   * and returns null; a network error is thrown and the session stays, so one bad connection does not sign
   * the user out.
   */
  refreshedAccessToken(old: string): Promise<string | null> {
    if (!this.refreshing) {
      this.refreshing = this.refresh(old).finally(() => {
        this.refreshing = null;
      });
    }
    return this.refreshing;
  }

  private async refresh(old: string): Promise<string | null> {
    const session = await this.session();
    if (!session) return null;
    if (session.accessToken !== old) return session.accessToken;
    try {
      const fresh = await this.client.refresh(session.refreshToken);
      await this.area.set({ [SESSION_KEY]: fresh });
      return fresh.accessToken;
    } catch (error) {
      if (error instanceof AuthError && error.kind === "invalidRefreshToken") {
        await this.area.remove(SESSION_KEY);
        return null;
      }
      // The card shows API errors: a network problem keeps the session and says so.
      if (error instanceof AuthError) throw new ApiError(error.kind === "network" ? "network" : "server", error.message);
      throw error;
    }
  }
}
