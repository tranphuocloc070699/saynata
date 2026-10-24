import { Refine, type DataProvider } from "@refinedev/core";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { UserDetail, UserRow } from "@/api/types";
import { ago, UsersPage } from "@/pages/UsersPage";
import { httpClient } from "@/providers/dataProvider";

const API = "https://api.test/api/admin";

const linh: UserRow = {
  id: 12,
  email: "linh@example.com",
  displayName: "linh",
  createdAt: "2026-10-12T08:00:00Z",
  lastSeenAt: new Date(Date.now() - 2 * 3600_000).toISOString(),
  blocked: false,
  admin: false,
  providers: ["google"],
  explanationsToday: 14,
  explanationsTotal: 312,
  costTotalUsd: 0.52,
};
const admin: UserRow = { ...linh, id: 1, email: "admin@example.com", admin: true, providers: [], costTotalUsd: 0 };
const spam: UserRow = { ...linh, id: 88, email: "spam@example.com", blocked: true, providers: [] };

function detailOf(user: UserRow): UserDetail {
  return {
    user,
    blockedReason: user.blocked ? "980 calls in one day" : null,
    blockedAt: user.blocked ? "2026-10-23T10:14:00Z" : null,
    today: { calls: 14, costUsd: 0.02 },
    last7Days: { calls: 86, costUsd: 0.14 },
    total: { calls: 312, costUsd: 0.52 },
    wordsTotal: 201,
    sentencesTotal: 111,
    days: Array.from({ length: 14 }, (_, i) => ({ date: `2026-10-${String(11 + i).padStart(2, "0")}`, calls: i })),
    identities: user.providers.map((provider) => ({ provider, email: user.email, since: "2026-10-15T00:00:00Z" })),
    likedCount: 34,
    devicesSignedIn: 2,
  };
}

afterEach(() => vi.restoreAllMocks());

function renderUsers(rows = [linh, admin, spam]) {
  let current = rows;
  const getList = vi.fn().mockImplementation(async ({ filters }) => {
    const blocked = filters?.some((f: { field: string }) => f.field === "blocked");
    const data = blocked ? current.filter((u) => u.blocked) : current;
    return { data, total: data.length };
  });
  const getOne = vi.fn().mockImplementation(async ({ id }) => ({ data: detailOf(current.find((u) => u.id === Number(id))!) }));
  const custom = vi.fn().mockResolvedValue({
    data: { accounts: 121, activeToday: 18, explanationsToday: 243, costTodayUsd: 0.41, usageSince: "2026-10-24T00:00:00Z" },
  });
  const post = vi.spyOn(httpClient, "post").mockImplementation(async (url) => {
    current = current.map((u) => (url.endsWith("/12/block") && u.id === 12 ? { ...u, blocked: true } : u));
    return { data: {} };
  });
  const del = vi.spyOn(httpClient, "delete").mockImplementation(async () => {
    current = current.filter((u) => u.id !== 12);
    return { data: {} };
  });
  const dataProvider = { getList, getOne, custom, getApiUrl: () => API } as unknown as DataProvider;
  render(
    <MemoryRouter>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <UsersPage />
      </Refine>
    </MemoryRouter>,
  );
  return { getList, post, del, custom };
}

async function openRow(email: string) {
  const table = await screen.findByRole("table", { name: "Users" });
  await userEvent.click((await within(table).findByText(email)).closest("tr")!);
  return screen.findByRole("region", { name: "Account" });
}

describe("users page (TASK-105)", () => {
  it("shows the totals and a row per account with sign-in methods, usage and only Blocked/Admin badges", async () => {
    renderUsers();
    const totals = screen.getByRole("group", { name: "Totals" });
    expect(await within(totals).findByText("121")).toBeInTheDocument();
    expect(within(totals).getByText("$0.41")).toBeInTheDocument();
    expect(screen.getByText(/counted since 10-24/)).toBeInTheDocument();

    const table = await screen.findByRole("table", { name: "Users" });
    const row = (await within(table).findByText("linh@example.com")).closest("tr")!;
    expect(within(row).getByLabelText("Google")).toBeInTheDocument();
    expect(within(row).getByText("2 h ago")).toBeInTheDocument();
    expect(within(row).getByText("312")).toBeInTheDocument();
    expect(within(row).getByText("$0.52")).toBeInTheDocument();
    expect(within(row).queryByText(/Blocked|Admin/)).not.toBeInTheDocument();
    expect(within(within(table).getByText("admin@example.com").closest("tr")!).getByText("Admin")).toBeInTheDocument();
    const spamRow = within(table).getByText("spam@example.com").closest("tr")!;
    expect(within(spamRow).getByText("Blocked")).toBeInTheDocument();
    expect(within(spamRow).getByLabelText("Email code")).toBeInTheDocument();
  });

  it("sends the Blocked choice of the Filter menu to the backend", async () => {
    const { getList } = renderUsers();
    await screen.findByText("linh@example.com");
    await userEvent.click(screen.getByRole("button", { name: /^Filter/ }));
    await userEvent.click(within(screen.getByRole("group", { name: "Filter choices" })).getByRole("button", { name: "Blocked" }));
    await vi.waitFor(() => expect(screen.queryByText("linh@example.com")).not.toBeInTheDocument());
    const listCall = getList.mock.calls.map((c) => c[0]).filter((p) => p.pagination?.pageSize === 50).at(-1);
    expect(listCall.filters).toEqual([{ field: "blocked", operator: "eq", value: true }]);
  });

  it("opens an account in the drawer with usage, sign-in methods and the account", async () => {
    renderUsers();
    const panel = await openRow("linh@example.com");
    expect(within(panel).getByRole("heading", { name: "linh@example.com" })).toBeInTheDocument();
    expect(within(panel).getByText("86")).toBeInTheDocument();
    expect(within(panel).getByRole("img", { name: "New explanations in the last 14 days" })).toBeInTheDocument();
    expect(within(panel).getByText(/words 201, sentences 111/)).toBeInTheDocument();
    expect(within(panel).getByText("Google")).toBeInTheDocument();
    expect(within(panel).getByText("34 words and sentences")).toBeInTheDocument();
  });

  it("blocks with a reason after asking, then reloads the account", async () => {
    const { post } = renderUsers();
    const panel = await openRow("linh@example.com");
    await userEvent.click(within(panel).getByRole("button", { name: "Block" }));
    await userEvent.type(within(panel).getByRole("textbox"), "spam");
    await userEvent.click(within(panel).getByRole("button", { name: "Confirm block" }));

    await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe(`${API}/users/12/block`);
    expect(post.mock.calls[0][1]).toEqual({ reason: "spam" });
    expect(post.mock.calls[0][2]).toMatchObject({ headers: { "Content-Type": "application/json" } });
    expect(await within(screen.getByRole("region", { name: "Account" })).findByRole("button", { name: "Unblock" })).toBeInTheDocument();
  });

  it("deletes only after the email is typed, then closes the drawer", async () => {
    const { del } = renderUsers();
    const panel = await openRow("linh@example.com");
    await userEvent.click(within(panel).getByRole("button", { name: "Delete account" }));
    const confirm = within(panel).getByRole("button", { name: "Confirm delete" });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(panel).getByRole("textbox"), "LINH@example.com");
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);

    await vi.waitFor(() => expect(del).toHaveBeenCalledTimes(1));
    expect(del.mock.calls[0][0]).toBe(`${API}/users/12`);
    await vi.waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.queryByText("linh@example.com")).not.toBeInTheDocument();
  });

  it("turns Block and Delete off for an admin account", async () => {
    renderUsers();
    const panel = await openRow("admin@example.com");
    expect(within(panel).getByRole("button", { name: "Block" })).toBeDisabled();
    expect(within(panel).getByRole("button", { name: "Delete account" })).toBeDisabled();
  });

  it("says when an account was last seen", () => {
    const now = Date.parse("2026-10-24T12:00:00Z");
    expect(ago(null, now)).toBe("never");
    expect(ago("2026-10-24T11:30:00Z", now)).toBe("this hour");
    expect(ago("2026-10-24T07:00:00Z", now)).toBe("5 h ago");
    expect(ago("2026-10-23T08:00:00Z", now)).toBe("yesterday");
    expect(ago("2026-10-20T08:00:00Z", now)).toBe("2026-10-20");
  });
});
