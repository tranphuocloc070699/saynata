import { Refine, type DataProvider } from "@refinedev/core";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { WordItem } from "@/api/types";
import { WordsPage } from "@/pages/WordsPage";

const TOTAL = 120;

const makeWord = (id: number): WordItem => ({
  id,
  word: `word${id}`,
  needsReview: id % 2 === 0,
  source: id % 3 === 0 ? "backend" : "bundled", // 40 of the 120 words are backend words
  model: "haiku",
  promptVersion: "v3",
  createdAt: "2026-10-14T10:00:00Z",
  entry: { senses: [{ pos: "noun", cefr: "A1", simpleDefinition: `Meaning ${id}.`, vietnamese: "x", examples: [], usage: [] }] },
});

// A fake backend with TOTAL words that answers by page, like the real one (_start/_end).
function renderWords() {
  const all = Array.from({ length: TOTAL }, (_, i) => makeWord(i + 1));
  const getList = vi.fn().mockImplementation(async ({ pagination, filters }) => {
    const filter = (field: string) => filters?.find((f: { field: string }) => f.field === field)?.value;
    const rows = all.filter(
      (w) => (filter("needs_review") === undefined || w.needsReview) && (filter("source") === undefined || w.source === filter("source")),
    );
    const { currentPage = 1, pageSize = 10 } = pagination ?? {};
    return { data: rows.slice((currentPage - 1) * pageSize, currentPage * pageSize), total: rows.length };
  });
  const dataProvider = { getList, getApiUrl: () => "https://api.test/api/admin" } as unknown as DataProvider;
  render(
    <MemoryRouter>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <WordsPage />
      </Refine>
    </MemoryRouter>,
  );
  return getList;
}

// jsdom has no IntersectionObserver. This one lets a test say "the end of the list is on screen".
let observers: { callback: IntersectionObserverCallback; disconnected: boolean }[] = [];
function scrollToEnd() {
  const live = observers.filter((o) => !o.disconnected).at(-1)!;
  act(() => live.callback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver));
}

beforeEach(() => {
  observers = [];
  vi.stubGlobal(
    "IntersectionObserver",
    class {
      entry: { callback: IntersectionObserverCallback; disconnected: boolean };
      constructor(callback: IntersectionObserverCallback) {
        this.entry = { callback, disconnected: false };
        observers.push(this.entry);
      }
      observe() {}
      unobserve() {}
      disconnect() {
        this.entry.disconnected = true;
      }
    },
  );
});
afterEach(() => vi.unstubAllGlobals());

describe("infinite scroll", () => {
  it("loads 50 words first, then the next page when the end of the list is on screen", async () => {
    const getList = renderWords();
    await screen.findByText("word50");
    expect(screen.queryByText("word51")).not.toBeInTheDocument();
    expect(getList).toHaveBeenCalledTimes(1);
    expect(getList.mock.calls[0][0].pagination).toMatchObject({ currentPage: 1, pageSize: 50 });

    scrollToEnd();

    expect(await screen.findByText("word51")).toBeInTheDocument();
    expect(getList.mock.calls[1][0].pagination).toMatchObject({ currentPage: 2, pageSize: 50 });
    expect(screen.getByText("word1")).toBeInTheDocument();
  });

  it("shows the count line with no arrows and stops loading at the total", async () => {
    renderWords();
    await screen.findByText("word50");
    expect(screen.getByText("50 of 120")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /page/i })).not.toBeInTheDocument();

    scrollToEnd();
    await screen.findByText("word100");
    expect(screen.getByText("100 of 120")).toBeInTheDocument();

    scrollToEnd();
    await screen.findByText("word120");
    expect(screen.getByText("120 of 120")).toBeInTheDocument();
    expect(observers.filter((o) => !o.disconnected)).toHaveLength(0);
  });

  it("starts again at the first page when a filter changes", async () => {
    const getList = renderWords();
    await screen.findByText("word50");
    scrollToEnd();
    await screen.findByText("word100");

    await userEvent.click(screen.getByRole("button", { name: "Needs review" }));

    await screen.findByText("50 of 60");
    const last = getList.mock.calls.at(-1)![0];
    expect(last.pagination.currentPage).toBe(1);
    expect(last.filters).toEqual([{ field: "needs_review", operator: "eq", value: true }]);
    expect(screen.queryByText("word51")).not.toBeInTheDocument();
  });

  it("From backend is a server filter: one request, the filtered total, and no extra pages", async () => {
    const getList = renderWords();
    await screen.findByText("word50");
    scrollToEnd();
    await screen.findByText("word100"); // 2 pages are loaded
    const callsBefore = getList.mock.calls.length;

    await userEvent.click(screen.getByRole("button", { name: "From backend" }));

    await screen.findByText("40 of 40");
    expect(screen.getByText("word3")).toBeInTheDocument();
    expect(screen.queryByText("word1")).not.toBeInTheDocument();
    expect(screen.queryByText("Nothing found.")).not.toBeInTheDocument();
    // One request for page 1 with the source param. The 40 rows are all there is, so nothing more is asked.
    expect(getList).toHaveBeenCalledTimes(callsBefore + 1);
    const last = getList.mock.calls.at(-1)![0];
    expect(last.pagination.currentPage).toBe(1);
    expect(last.filters).toEqual([{ field: "source", operator: "eq", value: "backend" }]);
    expect(observers.filter((o) => !o.disconnected)).toHaveLength(0);
    expect(screen.queryByRole("status", { name: "Loading more" })).not.toBeInTheDocument();
  });

  it("keeps the selected word selected when more pages load", async () => {
    renderWords();
    await userEvent.click(await screen.findByText("word3"));
    scrollToEnd();
    await screen.findByText("word51");

    // The word is open in the drawer (a modal), so the table behind it is hidden from the accessibility tree.
    const list = screen.getByRole("table", { name: "Rows", hidden: true });
    expect(within(screen.getByRole("region", { name: "Entry" })).getByRole("heading", { name: "word3" })).toBeInTheDocument();
    expect(within(list).getByText("word3").closest("button")).toHaveAttribute("aria-pressed", "true");
  });
});
