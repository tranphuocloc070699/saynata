import { Refine, type DataProvider } from "@refinedev/core";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { PrefillQueueItem, WordItem } from "@/api/types";
import { WordsPage } from "@/pages/WordsPage";
import { httpClient } from "@/providers/dataProvider";

const API = "https://api.test/api/admin";

const house: WordItem = {
  id: 2,
  word: "house",
  needsReview: false,
  source: "bundled",
  model: "haiku",
  promptVersion: "v3",
  createdAt: "2026-10-14T10:00:00Z",
  entry: { senses: [{ pos: "noun", cefr: "A1", simpleDefinition: "A home.", vietnamese: "ngôi nhà", examples: [], usage: [] }] },
};

const lantern: PrefillQueueItem = {
  id: 7,
  kind: "word",
  text: "lantern",
  targetLang: "",
  source: "sentence-words",
  seenCount: 3,
  priority: 400,
  status: "pending",
  attempts: 0,
  createdAt: "2026-10-23T02:00:00Z",
  updatedAt: "2026-10-23T02:00:00Z",
};

afterEach(() => vi.restoreAllMocks());

function renderWords() {
  let queue = [lantern];
  const getList = vi.fn().mockImplementation(async ({ resource, filters }) => {
    if (resource === "prefill/items") return { data: queue, total: queue.length };
    const needsReview = filters?.some((f: { field: string }) => f.field === "needs_review");
    const data = needsReview ? [] : [house];
    return { data, total: data.length };
  });
  const del = vi.spyOn(httpClient, "delete").mockImplementation(async () => {
    queue = [];
    return { data: { ...lantern, status: "skipped" } };
  });
  const dataProvider = { getList, getApiUrl: () => API } as unknown as DataProvider;
  render(
    <MemoryRouter>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <WordsPage />
      </Refine>
    </MemoryRouter>,
  );
  return { getList, del };
}

async function choose(label: string) {
  if (!screen.queryByRole("group", { name: "Filter choices" })) {
    await userEvent.click(screen.getByRole("button", { name: /^Filter/ }));
  }
  await userEvent.click(within(screen.getByRole("group", { name: "Filter choices" })).getByRole("button", { name: label }));
}

describe("Pending (TASK-101)", () => {
  it("shows the pending count in the menu and the queue rows of this kind when chosen", async () => {
    const { getList } = renderWords();
    await screen.findByText("house");
    await userEvent.click(screen.getByRole("button", { name: /^Filter/ }));
    await vi.waitFor(() => expect(screen.getByRole("button", { name: "Pending" })).toHaveTextContent(/1$/));

    await choose("Pending");

    const table = await screen.findByRole("table", { name: "Pending" });
    const row = (await within(table).findByText("lantern")).closest("tr")!;
    expect(within(row).getByText("3")).toBeInTheDocument();
    expect(within(row).getByText("In a sentence")).toBeInTheDocument();
    expect(within(row).getByText("Pending")).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Rows" })).not.toBeInTheDocument();
    const queueCall = getList.mock.calls.find((c) => c[0].resource === "prefill/items" && c[0].pagination?.pageSize === 50);
    expect(queueCall![0].filters).toEqual([
      { field: "kind", operator: "eq", value: "word" },
      { field: "status", operator: "eq", value: "pending" },
    ]);
  });

  it("is on alone: Pending turns the other choices off, and another choice turns Pending off", async () => {
    renderWords();
    await screen.findByText("house");
    await choose("Bundled");
    await choose("Pending");
    expect(screen.getByRole("button", { name: "Bundled" })).toHaveAttribute("aria-pressed", "false");
    expect(await screen.findByRole("table", { name: "Pending" })).toBeInTheDocument();

    await choose("Needs review");
    expect(screen.getByRole("button", { name: "Pending" })).toHaveAttribute("aria-pressed", "false");
    expect(await screen.findByRole("table", { name: "Rows" })).toBeInTheDocument();
  });

  it("removes a row after asking, then reloads the rows and the count", async () => {
    const { del } = renderWords();
    await screen.findByText("house");
    await choose("Pending");
    const table = await screen.findByRole("table", { name: "Pending" });
    await within(table).findByText("lantern");

    await userEvent.click(screen.getByRole("button", { name: "Remove lantern from the queue" }));
    // Cancel changes nothing.
    await userEvent.click(screen.getByRole("button", { name: "Cancel remove" }));
    expect(del).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "Remove lantern from the queue" }));
    await userEvent.click(screen.getByRole("button", { name: "Confirm remove" }));

    await vi.waitFor(() => expect(del).toHaveBeenCalledTimes(1));
    expect(del.mock.calls[0][0]).toBe(`${API}/prefill/items/7`);
    expect(del.mock.calls[0][1]).toMatchObject({ headers: { "Content-Type": "application/json" } });
    expect(await screen.findByText("Nothing is waiting for the night job.")).toBeInTheDocument();
    // The menu closed when the row was clicked; open it again to read the count.
    await userEvent.click(screen.getByRole("button", { name: /^Filter/ }));
    await vi.waitFor(() => expect(screen.getByRole("button", { name: "Pending" })).toHaveTextContent(/0$/));
  });
});
