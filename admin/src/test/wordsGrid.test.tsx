import { Refine, type DataProvider } from "@refinedev/core";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SenseMedia, WordItem } from "@/api/types";
import { WordsPage } from "@/pages/WordsPage";

const API = "https://api.test/api/admin";
const VIEW_KEY = "admin.words.view";

function media(kind: "photo" | "clip", name: string, source?: SenseMedia["source"]): SenseMedia {
  return { url: `https://api.loffy.me/media/${name}`, kind, photographer: "Ann", pageUrl: "https://pixabay.com/x/", source };
}

function word(id: number, text: string, vietnamese: string, first: SenseMedia | null, extra: Partial<WordItem> = {}): WordItem {
  return {
    id,
    word: text,
    needsReview: false,
    source: "bundled",
    model: "haiku",
    promptVersion: "v3",
    createdAt: "2026-10-14T10:00:00Z",
    entry: {
      senses: [{ pos: "noun", cefr: "A1", simpleDefinition: `English words about ${text}.`, vietnamese, examples: [], usage: [] }],
    },
    media: [first],
    mediaStatus: [null],
    ...extra,
  };
}

const apple = word(1, "apple", "quả táo", media("photo", "apple.webp"), { mediaToReviewCount: 2 });
const run = word(2, "run", "chạy", media("clip", "run.mp4"));
const idea = word(3, "idea", "ý tưởng", null);

function renderWords(words: WordItem[]) {
  const getList = vi.fn().mockResolvedValue({ data: words, total: words.length });
  const dataProvider = { getList, getApiUrl: () => API } as unknown as DataProvider;
  const result = render(
    <MemoryRouter>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <WordsPage />
      </Refine>
    </MemoryRouter>,
  );
  return { getList, ...result };
}

beforeEach(() => {
  localStorage.clear();
  // jsdom has no ResizeObserver; the hover card (Radix Popper) needs one to place itself.
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("words grid view (TASK-81)", () => {
  it("starts as a list, and the two toggle buttons have only icons with labels", async () => {
    renderWords([apple]);
    const list = await screen.findByRole("region", { name: "List" });
    await within(list).findByText("apple");

    expect(screen.getByRole("button", { name: "List view" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Grid view" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Grid view" })).toHaveTextContent("");
    expect(screen.queryByRole("list", { name: "Grid" })).not.toBeInTheDocument();
  });

  it("switches to the grid, saves the choice, and the next page load starts in the grid", async () => {
    const user = userEvent.setup();
    const first = renderWords([apple, run, idea]);
    await screen.findByText("apple");

    await user.click(screen.getByRole("button", { name: "Grid view" }));

    expect(localStorage.getItem(VIEW_KEY)).toBe("grid");
    expect(screen.getByRole("list", { name: "Grid" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Grid view" })).toHaveAttribute("aria-pressed", "true");
    // The grid has the whole width: no "Pick an entry" pane next to it.
    expect(screen.queryByText("Pick an entry from the list.")).not.toBeInTheDocument();

    first.unmount();
    renderWords([apple]);
    expect(await screen.findByRole("list", { name: "Grid" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "List view" }));
    expect(localStorage.getItem(VIEW_KEY)).toBe("list");
    expect(screen.queryByRole("list", { name: "Grid" })).not.toBeInTheDocument();
  });

  it("still works when the browser blocks storage", async () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    const user = userEvent.setup();
    renderWords([apple]);
    await screen.findByText("apple");

    await user.click(screen.getByRole("button", { name: "Grid view" }));

    expect(screen.getByRole("list", { name: "Grid" })).toBeInTheDocument();
  });

  it("shows a card per word: thumbnail, word, Vietnamese, and the same chips as the list row", async () => {
    localStorage.setItem(VIEW_KEY, "grid");
    renderWords([apple, run, idea]);
    const grid = await screen.findByRole("list", { name: "Grid" });
    const card = (await within(grid).findByText("apple")).closest("button") as HTMLElement;

    expect(within(card).getByRole("img", { name: "apple photo" })).toHaveAttribute("src", "https://api.loffy.me/media/apple.webp");
    expect(within(card).getByText("quả táo")).toBeInTheDocument();
    expect(within(card).getByText("Bundled")).toBeInTheDocument();
    expect(within(card).getByText("2 to review")).toBeInTheDocument();

    const clipCard = within(grid).getByText("run").closest("button") as HTMLElement;
    expect(within(clipCard).getByRole("img", { name: "run clip" })).toBeInTheDocument();
    const noMedia = within(grid).getByText("idea").closest("button") as HTMLElement;
    expect(within(noMedia).getByLabelText("No media")).toBeInTheDocument();
  });

  it("opens the word in a drawer when a card is clicked, and marks the card as selected", async () => {
    localStorage.setItem(VIEW_KEY, "grid");
    const user = userEvent.setup();
    renderWords([apple, run]);
    const grid = await screen.findByRole("list", { name: "Grid" });
    const card = (await within(grid).findByText("apple")).closest("button") as HTMLElement;

    await user.click(card);

    const drawer = await screen.findByRole("dialog");
    expect(within(drawer).getByRole("heading", { name: "apple" })).toBeInTheDocument();
    expect(within(drawer).getByText("English words about apple.")).toBeInTheDocument();
    expect(card).toHaveAttribute("aria-pressed", "true");

    await user.click(within(drawer).getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(card).toHaveAttribute("aria-pressed", "false");
  });

  it("keeps the filters working in the grid (the chip goes to the backend)", async () => {
    localStorage.setItem(VIEW_KEY, "grid");
    const user = userEvent.setup();
    const { getList } = renderWords([apple]);
    await screen.findByText("apple");

    await user.click(screen.getByRole("button", { name: "Bundled" }));

    await waitFor(() =>
      expect(getList.mock.calls.some(([args]) => (args.filters ?? []).some((f: { field: string; value: unknown }) => f.field === "source" && f.value === "bundled"))).toBe(
        true,
      ),
    );
  });
});

describe("thumbnail preview card (TASK-81)", () => {
  it("opens after a short delay with the full-size image and a caption (sense, source)", async () => {
    const user = userEvent.setup();
    renderWords([apple]);
    const thumb = await screen.findByRole("img", { name: "apple photo" });

    await user.hover(thumb);

    expect(await screen.findByRole("img", { name: "apple preview" }, { timeout: 2000 })).toHaveAttribute(
      "src",
      "https://api.loffy.me/media/apple.webp",
    );
    expect(screen.getByText("apple · sense 1 · Pixabay")).toBeInTheDocument();
  });

  it("plays a clip muted and looped in the preview, and names an AI source and a wrong picture", async () => {
    const wrongClip = word(2, "run", "chạy", media("clip", "run.mp4", "ai"), {
      mediaReview: [{ state: "wrong" }],
    });
    vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue(undefined);
    const user = userEvent.setup();
    localStorage.setItem(VIEW_KEY, "grid");
    renderWords([wrongClip]);
    const thumb = await screen.findByRole("img", { name: "run clip" });

    await user.hover(thumb);

    const video = await screen.findByLabelText("run clip preview", undefined, { timeout: 2000 });
    expect(video).toHaveAttribute("src", "https://api.loffy.me/media/run.mp4");
    expect((video as HTMLVideoElement).muted).toBe(true);
    expect((video as HTMLVideoElement).loop).toBe(true);
    expect(screen.getByText("run · sense 1 · AI · Wrong picture")).toBeInTheDocument();
  });
});
