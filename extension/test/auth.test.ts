import { describe, expect, it } from "vitest";
import { ApiError, SaynataApi } from "../src/shared/api";
import { AuthClient, AuthError, AuthStore, SESSION_KEY, type Session } from "../src/shared/auth";
import { fakeFetch, json, wordResponse } from "./helpers";

const BASE = "https://api.test";
const session = (access = "a1", refresh = "r1"): Session => ({ accessToken: access, refreshToken: refresh, user: { id: 7, email: "lan@example.com" } });
const pair = (access: string, refresh: string) => ({ ...session(access, refresh), expiresIn: 900 });

function area(initial: Record<string, unknown> = {}) {
  const data: Record<string, unknown> = { ...initial };
  return {
    data,
    fake: {
      get: async (keys: string[]) => Object.fromEntries(keys.filter((key) => key in data).map((key) => [key, data[key]])),
      set: async (items: Record<string, unknown>) => void Object.assign(data, items),
      remove: async (key: string) => void delete data[key],
    },
  };
}

describe("AuthClient", () => {
  it("posts the email, then the email and code", async () => {
    const { fetchImpl, calls } = fakeFetch(() => new Response(null, { status: 204 }), () => json(pair("a1", "r1")));
    const client = new AuthClient(BASE, fetchImpl);
    await client.requestCode("lan@example.com");
    const signedIn = await client.verify("lan@example.com", "123456");
    expect(calls.map((c) => c.url)).toEqual([`${BASE}/api/auth/email-code`, `${BASE}/api/auth/verify`]);
    expect(JSON.parse(String(calls[1].init.body))).toEqual({ email: "lan@example.com", code: "123456" });
    expect(signedIn).toEqual(session());
  });

  it("maps the error codes to the app's texts", async () => {
    const cases: [Response, string][] = [
      [json({ code: "invalid_code" }, 401), "That code is wrong or has expired."],
      [json({ code: "invalid_email", detail: "Enter a valid email." }, 400), "Enter a valid email."],
      [json({ code: "rate_limited" }, 429, { "Retry-After": "600" }), "Too many requests. Try again in 10 minutes."],
      [json({ code: "invalid_refresh_token" }, 401), "Your session has ended. Sign in again."],
      [json({}, 500), "The server answered with status 500."],
    ];
    for (const [response, message] of cases) {
      const { fetchImpl } = fakeFetch(() => response);
      await expect(new AuthClient(BASE, fetchImpl).verify("a@b.co", "1")).rejects.toMatchObject({ message });
    }
  });

  it("a network failure is its own kind", async () => {
    const { fetchImpl } = fakeFetch(() => {
      throw new TypeError("offline");
    });
    await expect(new AuthClient(BASE, fetchImpl).requestCode("a@b.co")).rejects.toMatchObject({ kind: "network" });
  });
});

describe("AuthStore", () => {
  it("saves the session after a right code and gives its access token", async () => {
    const { fake, data } = area();
    const { fetchImpl } = fakeFetch(() => json(pair("a1", "r1")));
    const store = new AuthStore(fake, new AuthClient(BASE, fetchImpl));
    expect(await store.accessToken()).toBeNull();
    expect((await store.signIn(" lan@example.com ", "123456")).email).toBe("lan@example.com");
    expect(data[SESSION_KEY]).toEqual(session());
    expect(await store.accessToken()).toBe("a1");
  });

  it("signs out on this browser even when the server call fails", async () => {
    const { fake, data } = area({ [SESSION_KEY]: session() });
    const { fetchImpl, calls } = fakeFetch(() => {
      throw new TypeError("offline");
    });
    await new AuthStore(fake, new AuthClient(BASE, fetchImpl)).signOut();
    expect(data[SESSION_KEY]).toBeUndefined();
    expect(JSON.parse(String(calls[0].init.body))).toEqual({ refreshToken: "r1" });
  });

  it("requests that fail together share one refresh", async () => {
    const { fake, data } = area({ [SESSION_KEY]: session() });
    const { fetchImpl, calls } = fakeFetch(() => json(pair("a2", "r2")));
    const store = new AuthStore(fake, new AuthClient(BASE, fetchImpl));
    const [first, second] = await Promise.all([store.refreshedAccessToken("a1"), store.refreshedAccessToken("a1")]);
    expect([first, second]).toEqual(["a2", "a2"]);
    expect(calls).toHaveLength(1);
    expect(JSON.parse(String(calls[0].init.body))).toEqual({ refreshToken: "r1" });
    expect((data[SESSION_KEY] as Session).refreshToken).toBe("r2");
  });

  it("returns the newer token when someone already refreshed", async () => {
    const { fake } = area({ [SESSION_KEY]: session("a2", "r2") });
    const { fetchImpl, calls } = fakeFetch();
    expect(await new AuthStore(fake, new AuthClient(BASE, fetchImpl)).refreshedAccessToken("a1")).toBe("a2");
    expect(calls).toHaveLength(0);
  });

  it("a refused refresh signs out and returns null", async () => {
    const { fake, data } = area({ [SESSION_KEY]: session() });
    const { fetchImpl } = fakeFetch(() => json({ code: "invalid_refresh_token" }, 401));
    expect(await new AuthStore(fake, new AuthClient(BASE, fetchImpl)).refreshedAccessToken("a1")).toBeNull();
    expect(data[SESSION_KEY]).toBeUndefined();
  });

  it("a network error keeps the session", async () => {
    const { fake, data } = area({ [SESSION_KEY]: session() });
    const { fetchImpl } = fakeFetch(() => {
      throw new TypeError("offline");
    });
    const error = await new AuthStore(fake, new AuthClient(BASE, fetchImpl)).refreshedAccessToken("a1").catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ kind: "network" });
    expect(data[SESSION_KEY]).toEqual(session());
  });

  it("works as the API's token source: Bearer, then refresh and retry on a 401", async () => {
    const { fake } = area({ [SESSION_KEY]: session() });
    const auth = fakeFetch(() => json(pair("a2", "r2")));
    const store = new AuthStore(fake, new AuthClient(BASE, auth.fetchImpl));
    const api = fakeFetch(() => json({ status: 401 }, 401), () => json(wordResponse));
    await new SaynataApi(BASE, store, api.fetchImpl).lookUpWord("zyzzyva");
    expect((api.calls[0].init.headers as Record<string, string>).Authorization).toBe("Bearer a1");
    expect((api.calls[1].init.headers as Record<string, string>).Authorization).toBe("Bearer a2");
  });

  it("AuthError keeps its kind", () => {
    expect(new AuthError("invalidCode", "x").kind).toBe("invalidCode");
  });
});
