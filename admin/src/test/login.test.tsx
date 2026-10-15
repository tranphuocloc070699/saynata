import { Refine } from "@refinedev/core";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LoginPage } from "@/pages/LoginPage";
import { authProvider } from "@/providers/authProvider";

function mockFetch(status: number, body: unknown = {}) {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("auth provider", () => {
  it("posts the credentials with the cookie included", async () => {
    const fetchMock = mockFetch(200);
    const result = await authProvider.login({ email: "a@b.com", password: "secret" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/admin\/auth\/login$/);
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("include");
    expect(JSON.parse(init.body)).toEqual({ email: "a@b.com", password: "secret" });
    expect(result.success).toBe(true);
  });

  it("says the login failed on a 401", async () => {
    mockFetch(401);
    const result = await authProvider.login({ email: "a@b.com", password: "wrong" });
    expect(result.success).toBe(false);
    expect(result.error?.message).toBe("Wrong email or password.");
  });

  it("is signed in only when /me returns an ADMIN", async () => {
    mockFetch(200, { id: 1, email: "a@b.com", displayName: "Admin", roles: ["ADMIN"] });
    expect((await authProvider.check({})).authenticated).toBe(true);

    mockFetch(401);
    const result = await authProvider.check({});
    expect(result.authenticated).toBe(false);
    expect(result.redirectTo).toBe("/login");
  });
});

describe("login page", () => {
  function renderLogin() {
    return render(
      <MemoryRouter>
        <Refine dataProvider={{ getApiUrl: () => "" } as never} authProvider={authProvider} options={{ disableTelemetry: true }}>
          <LoginPage />
        </Refine>
      </MemoryRouter>,
    );
  }

  it("sends the form and shows an error for a wrong password", async () => {
    const fetchMock = mockFetch(401);
    renderLogin();

    await userEvent.type(screen.getByLabelText("Email"), "a@b.com");
    await userEvent.type(screen.getByLabelText("Password"), "wrong");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    // Refine also asks /me when it starts, so look for the login call among the calls.
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith("/auth/login"))).toBe(true));
    expect(await screen.findByRole("alert")).toHaveTextContent("Wrong email or password.");
  });
});
