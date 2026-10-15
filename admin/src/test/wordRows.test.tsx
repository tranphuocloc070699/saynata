import { Refine, type DataProvider } from "@refinedev/core";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { SenseMedia, WordItem } from "@/api/types";
import { httpClient } from "@/providers/dataProvider";
import { WordsPage } from "@/pages/WordsPage";

const API = "https://api.test/api/admin";

function media(kind: "photo" | "clip", name: string): SenseMedia {
  return { url: `https://api.loffy.me/media/${name}`, kind, photographer: `By ${name}`, pageUrl: `https://pixabay.com/x/${name}/` };
}

function word(id: number, text: string, vietnamese: string, first: SenseMedia | null): WordItem {
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
  };
}

const photoWord = word(1, "apple", "quả táo", media("photo", "apple.webp"));
const clipWord = word(2, "run", "chạy", media("clip", "run.mp4"));
const plainWord = word(3, "idea", "ý tưởng", null);

function renderWords(words: WordItem[]) {
  const getList = vi.fn().mockResolvedValue({ data: words, total: words.length });
  const dataProvider = { getList, getApiUrl: () => API } as unknown as DataProvider;
  render(
    <MemoryRouter>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <WordsPage />
      </Refine>
    </MemoryRouter>,
  );
}

async function row(name: string) {
  const list = await screen.findByRole("region", { name: "List" });
  return (await within(list).findByText(name)).closest("button") as HTMLElement;
}

describe("word list rows (TASK-62)", () => {
  it("shows a lazy photo thumbnail, the word, and the Vietnamese meaning instead of the English explanation", async () => {
    renderWords([photoWord]);
    const apple = await row("apple");

    const thumb = within(apple).getByRole("img", { name: "apple photo" });
    expect(thumb).toHaveAttribute("src", "https://api.loffy.me/media/apple.webp");
    expect(thumb).toHaveAttribute("loading", "lazy");
    expect(within(apple).getByText("quả táo")).toBeInTheDocument();
    expect(within(apple).getByText("Bundled")).toBeInTheDocument();
    expect(within(apple).queryByText(/English words about/)).not.toBeInTheDocument();
  });

  it("shows a play icon for a clip and does not load a video in the list", async () => {
    renderWords([clipWord]);
    const run = await row("run");

    expect(within(run).getByRole("img", { name: "run clip" })).toBeInTheDocument();
    expect(run.querySelector("video")).toBeNull();
  });

  it("shows a quiet dash when the word has no media", async () => {
    renderWords([plainWord]);
    const idea = await row("idea");

    expect(within(idea).getByLabelText("No media")).toHaveTextContent("-");
    expect(idea.querySelector("img")).toBeNull();
  });
});

describe("media row per word (TASK-64)", () => {
  beforeEach(() => {
    vi.spyOn(httpClient, "get").mockResolvedValue({
      data: { query: "apple", results: [{ kind: "photo", pixabayId: 1, previewUrl: "https://cdn.pixabay.com/p/1.jpg", downloadUrl: "https://cdn.pixabay.com/d/1", photographer: "P", pageUrl: "https://pixabay.com/x-1/" }] },
    });
  });

  it("closes the Pick another panel and shows the new word's media when another word is selected", async () => {
    renderWords([photoWord, clipWord]);

    await userEvent.click(await row("apple"));
    const entry = screen.getByRole("region", { name: "Entry" });
    expect(within(entry).getByRole("img", { name: "apple, sense 1" })).toHaveAttribute("src", "https://api.loffy.me/media/apple.webp");

    await userEvent.click(within(entry).getByRole("button", { name: "Pick another image" }));
    expect(await within(entry).findByRole("region", { name: "Pick another image (sense 1)" })).toBeInTheDocument();

    await userEvent.click(await row("run"));

    expect(within(entry).queryByRole("region", { name: /Pick another image/ })).not.toBeInTheDocument();
    expect(within(entry).queryByRole("img", { name: "apple, sense 1" })).not.toBeInTheDocument();
    const clip = within(entry).getByLabelText("run, sense 1");
    expect(clip.tagName).toBe("VIDEO");
    expect(clip).toHaveAttribute("src", "https://api.loffy.me/media/run.mp4");
    expect(within(entry).getByRole("link", { name: "By run.mp4" })).toBeInTheDocument();
  });
});
