import { Refine } from "@refinedev/core";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { Layout } from "@/components/Layout";

function Where() {
  const { pathname, search } = useLocation();
  return <p data-testid="where">{pathname + search}</p>;
}

function renderShell(path = "/sentences") {
  const logout = vi.fn().mockResolvedValue({ success: true });
  render(
    <MemoryRouter initialEntries={[path]}>
      <Refine
        dataProvider={{ getApiUrl: () => "" } as never}
        authProvider={{
          login: vi.fn(),
          logout,
          check: vi.fn().mockResolvedValue({ authenticated: true }),
          onError: vi.fn(),
          getIdentity: vi.fn().mockResolvedValue({ id: 1, name: "Loc", email: "loc@example.com" }),
        }}
        options={{ disableTelemetry: true }}
      >
        <Routes>
          <Route element={<Layout />}>
            <Route path="*" element={<Where />} />
          </Route>
        </Routes>
      </Refine>
    </MemoryRouter>,
  );
  return { logout };
}

describe("shell", () => {
  it("shows the page title and breadcrumb for the current section", () => {
    renderShell("/sentences");
    expect(screen.getByRole("heading", { level: 1, name: "Sentences" })).toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: "Breadcrumb" })).toHaveTextContent("Content·Sentences");
  });

  it("the top bar search opens the Words page with the text as its search", async () => {
    renderShell("/sentences");
    await userEvent.type(screen.getByRole("searchbox", { name: "Search words" }), "give up{Enter}");
    expect(screen.getByTestId("where")).toHaveTextContent("/words?q=give%20up");
  });

  it("the account menu shows who is signed in and signs out", async () => {
    const { logout } = renderShell();
    await userEvent.click(await screen.findByRole("button", { name: "Account" }));
    expect(await screen.findByText("loc@example.com")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    expect(logout).toHaveBeenCalled();
  });
});
