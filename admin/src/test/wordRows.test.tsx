import { Refine, type DataProvider } from "@refinedev/core";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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
  return getList;
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

describe("hover to play a clip in the list (TASK-77)", () => {
  const play = vi.spyOn(HTMLMediaElement.prototype, "play");
  const pause = vi.spyOn(HTMLMediaElement.prototype, "pause");
  const walkWord = word(4, "walk", "đi bộ", media("clip", "walk.mp4"));

  beforeEach(() => {
    play.mockReset().mockResolvedValue(undefined);
    pause.mockReset().mockImplementation(() => undefined);
  });

  it("plays the clip muted and looping while the mouse is over the row, then pauses and shows the still again", async () => {
    renderWords([clipWord, walkWord]);
    const run = await row("run");
    expect(document.querySelectorAll("video")).toHaveLength(0);

    await userEvent.hover(run);

    const video = run.querySelector("video") as HTMLVideoElement;
    expect(video).toHaveAttribute("src", "https://api.loffy.me/media/run.mp4");
    expect(video.muted).toBe(true);
    expect(video.loop).toBe(true);
    expect(play).toHaveBeenCalledTimes(1);
    // The still (play icon) is still under the video.
    expect(within(run).getByRole("img", { name: "run clip" })).toBeInTheDocument();

    await userEvent.unhover(run);

    expect(pause).toHaveBeenCalled();
    expect(run.querySelector("video")).toBeNull();
  });

  it("has a video in one row at a time", async () => {
    renderWords([clipWord, walkWord]);
    const run = await row("run");
    const walk = await row("walk");

    await userEvent.hover(run);
    expect(document.querySelectorAll("video")).toHaveLength(1);

    await userEvent.hover(walk);
    expect(run.querySelector("video")).toBeNull();
    expect(walk.querySelector("video")).not.toBeNull();
    expect(document.querySelectorAll("video")).toHaveLength(1);
  });

  it("does nothing on hover for a photo or a word without media", async () => {
    renderWords([photoWord, plainWord]);
    await userEvent.hover(await row("apple"));
    await userEvent.hover(await row("idea"));
    expect(document.querySelectorAll("video")).toHaveLength(0);
    expect(play).not.toHaveBeenCalled();
  });

  it("does nothing for a touch pointer", async () => {
    renderWords([clipWord]);
    const run = await row("run");
    // A tap on a phone: the pointer that enters the row is a finger.
    fireEvent.pointerEnter(run, { pointerType: "touch" });
    expect(run.querySelector("video")).toBeNull();
    expect(play).not.toHaveBeenCalled();
  });
});

describe("remove all media of a word (TASK-76)", () => {
  const del = vi.spyOn(httpClient, "delete");
  afterEach(() => vi.clearAllMocks());

  it("is an icon-only button next to the other header buttons, and disabled when the word has no media", async () => {
    renderWords([plainWord]);
    await userEvent.click(await row("idea"));
    const entry = screen.getByRole("region", { name: "Entry" });
    const button = within(entry).getByRole("button", { name: "Remove all media" });
    expect(button).toBeDisabled();
    expect(button).toHaveTextContent("");
    expect(button.querySelector("svg")).not.toBeNull();
  });

  it("asks first, then sends DELETE, and the list and the Media rows show no media afterwards", async () => {
    const words = [{ ...photoWord }, { ...clipWord }];
    del.mockImplementation(async () => {
      words[0] = { ...words[0], media: [null], mediaStatus: ["removed"] };
      return { data: {} };
    });
    const getList = renderWords(words);
    await userEvent.click(await row("apple"));
    const entry = screen.getByRole("region", { name: "Entry" });
    expect(within(entry).getByRole("img", { name: "apple, sense 1" })).toBeInTheDocument();

    await userEvent.click(within(entry).getByRole("button", { name: "Remove all media" }));
    expect(screen.getByRole("alertdialog")).toHaveTextContent("Remove the pictures and clips of all senses of apple? The app stops showing them.");
    expect(del).not.toHaveBeenCalled();

    // Cancel changes nothing.
    await userEvent.click(screen.getByRole("button", { name: "Cancel remove all media" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(del).not.toHaveBeenCalled();

    const callsBefore = getList.mock.calls.length;
    await userEvent.click(within(entry).getByRole("button", { name: "Remove all media" }));
    await userEvent.click(screen.getByRole("button", { name: "Confirm remove all media" }));

    await vi.waitFor(() => expect(del).toHaveBeenCalledTimes(1));
    expect(del.mock.calls[0][0]).toBe(`${API}/words/1/media`);
    // The infinite list is read again (PR #58), so the thumbnail and the Media row change.
    await vi.waitFor(() => expect(getList.mock.calls.length).toBeGreaterThan(callsBefore));
    await vi.waitFor(() => expect(within(entry).queryByRole("img", { name: "apple, sense 1" })).not.toBeInTheDocument());
    expect(within(await row("apple")).getByLabelText("No media")).toBeInTheDocument();
    expect(within(entry).getByRole("button", { name: "Remove all media" })).toBeDisabled();
  });

  it("shows the message when the backend refuses", async () => {
    del.mockRejectedValue(new Error("Request failed with status code 404"));
    renderWords([photoWord]);
    await userEvent.click(await row("apple"));
    const entry = screen.getByRole("region", { name: "Entry" });
    await userEvent.click(within(entry).getByRole("button", { name: "Remove all media" }));
    await userEvent.click(screen.getByRole("button", { name: "Confirm remove all media" }));
    expect(await within(entry).findByRole("alert")).toHaveTextContent("Request failed with status code 404");
  });
});
