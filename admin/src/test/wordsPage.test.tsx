import { Refine, type DataProvider } from "@refinedev/core";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import type { WordItem } from "@/api/types";
import { WordsPage } from "@/pages/WordsPage";

const run: WordItem = {
  id: 1,
  word: "run",
  ipa: "/rʌn/",
  needsReview: true,
  source: "backend",
  model: "haiku",
  promptVersion: "v3",
  createdAt: "2026-10-14T10:00:00Z",
  entry: {
    senses: [
      {
        pos: "verb",
        cefr: "A1",
        simpleDefinition: "To move fast on your legs.",
        vietnamese: "chạy",
        examples: [
          { level: "easy", text: "I run every day." },
          { level: "medium", text: "She runs to the station." },
          { level: "harder", text: "He ran until he was tired." },
        ],
        usage: [{ kind: "pattern", pattern: "run + to + place", example: "I run to school." }],
      },
    ],
  },
};

const house: WordItem = { ...run, id: 2, word: "house", needsReview: false, source: "bundled" };

function renderWords(words: WordItem[], path = "/words") {
  // A fake backend: it applies the filters it is sent, like the real list endpoint does.
  const getList = vi.fn().mockImplementation(async ({ filters }) => {
    const filter = (field: string) => filters?.find((f: { field: string }) => f.field === field)?.value;
    const data = words.filter(
      (w) =>
        (filter("needs_review") === undefined || w.needsReview) &&
        (filter("source") === undefined || w.source === filter("source")) &&
        (filter("cefr") === undefined || filter("cefr").includes(w.entry.senses[0].cefr)) &&
        (filter("media") === undefined ||
          (filter("media") === "wrong" ? (w.wrongMediaCount ?? 0) > 0 : filter("media") === "to-review" ? (w.mediaToReviewCount ?? 0) > 0 : (filter("media") === "with") === !!w.media?.some(Boolean))),
    );
    return { data, total: data.length };
  });
  const dataProvider = { getList, getApiUrl: () => "https://api.test/api/admin" } as unknown as DataProvider;

  render(
    <MemoryRouter initialEntries={[path]}>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <WordsPage />
      </Refine>
    </MemoryRouter>,
  );
  return getList;
}

describe("words page", () => {
  it("starts with the search from the top bar (?q=)", async () => {
    const getList = renderWords([run, house], "/words?q=run");
    expect(await screen.findByRole("searchbox", { name: "Search" })).toHaveValue("run");
    expect(getList.mock.calls[0][0].filters).toContainEqual({ field: "q", operator: "contains", value: "run" });
  });

  it("lists the words with a needs review badge", async () => {
    renderWords([run, house]);
    const list = await screen.findByRole("table", { name: "Rows" });
    expect(await within(list).findByText("run")).toBeInTheDocument();
    expect(within(list).getByText("house")).toBeInTheDocument();
    expect(within(list).getByText("Needs review")).toBeInTheDocument();
  });

  it("shows the words as a table with Source, Level, Review and Media columns (TASK-99.2)", async () => {
    renderWords([run, house]);
    const table = await screen.findByRole("table", { name: "Rows" });
    expect(within(table).getAllByRole("columnheader").map((th) => th.textContent)).toEqual(["Word", "Source", "Level", "Review", "Media"]);
    const houseRow = (await within(table).findByText("house")).closest("tr")!;
    expect(within(houseRow).getByText("Bundled")).toBeInTheDocument();
    expect(within(houseRow).getByText("A1")).toBeInTheDocument();
    expect(within(houseRow).getByText("Reviewed")).toBeInTheDocument();
    expect(within(houseRow).getByText("No media")).toBeInTheDocument();
  });

  it("opens the word in a drawer when its row is clicked, and Close shuts it", async () => {
    renderWords([run]);
    const table = await screen.findByRole("table", { name: "Rows" });
    await userEvent.click((await within(table).findByText("chạy")).closest("tr")!);
    expect(screen.getByRole("dialog", { name: "Entry details" })).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Entry" })).getByRole("heading", { name: "run" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("keeps the media chips in a Media menu that names the chosen one", async () => {
    renderWords([run, house]);
    await screen.findByText("house");
    expect(screen.queryByRole("button", { name: "Wrong media" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Media" }));
    await userEvent.click(within(screen.getByRole("group", { name: "Media filters" })).getByRole("button", { name: "Wrong media" }));
    expect(screen.getByRole("button", { name: "Media: Wrong media" })).toHaveAttribute("aria-expanded", "true");
  });

  it("asks the backend for needs_review=true when the chip is on", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");

    await userEvent.click(screen.getByRole("button", { name: "Needs review" }));

    await vi.waitFor(() => expect(screen.queryByText("house")).not.toBeInTheDocument());
    const lastCall = getList.mock.calls.at(-1)![0];
    expect(lastCall.filters).toEqual([{ field: "needs_review", operator: "eq", value: true }]);
    expect(screen.getByText("run")).toBeInTheDocument();
  });

  it("sends the source chip to the backend and shows only what the backend returns", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");
    getList.mockClear();

    await userEvent.click(screen.getByRole("button", { name: "Bundled" }));

    await vi.waitFor(() => expect(screen.queryByText("run")).not.toBeInTheDocument());
    expect(screen.getByText("house")).toBeInTheDocument();
    expect(getList).toHaveBeenCalledTimes(1);
    expect(getList.mock.calls[0][0].filters).toEqual([{ field: "source", operator: "eq", value: "bundled" }]);
  });

  it("Bundled and From backend work one at a time", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");

    await userEvent.click(screen.getByRole("button", { name: "Bundled" }));
    await userEvent.click(screen.getByRole("button", { name: "From backend" }));

    await vi.waitFor(() => expect(screen.queryByText("house")).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Bundled" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "From backend" })).toHaveAttribute("aria-pressed", "true");
    expect(getList.mock.calls.at(-1)![0].filters).toEqual([{ field: "source", operator: "eq", value: "backend" }]);
  });

  it("OR-s the level chips into one cefr list and combines them with the source chip", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");

    await userEvent.click(screen.getByRole("button", { name: "A1-A2" }));
    await userEvent.click(screen.getByRole("button", { name: "B1-B2" }));
    await userEvent.click(screen.getByRole("button", { name: "Bundled" }));

    await vi.waitFor(() =>
      expect(getList.mock.calls.at(-1)![0].filters).toEqual([
        { field: "source", operator: "eq", value: "bundled" },
        { field: "cefr", operator: "in", value: ["A1", "A2", "B1", "B2"] },
      ]),
    );
    // Turning a level chip off leaves the other one.
    await userEvent.click(screen.getByRole("button", { name: "A1-A2" }));
    await vi.waitFor(() =>
      expect(getList.mock.calls.at(-1)![0].filters).toContainEqual({ field: "cefr", operator: "in", value: ["B1", "B2"] }),
    );
  });

  it("sends media=with or media=without, one at a time", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");
    // The media chips sit in the Media menu (TASK-99.2).
    await userEvent.click(screen.getByRole("button", { name: "Media" }));

    await userEvent.click(screen.getByRole("button", { name: "Has media" }));
    await vi.waitFor(() =>
      expect(getList.mock.calls.at(-1)![0].filters).toEqual([{ field: "media", operator: "eq", value: "with" }]),
    );

    await userEvent.click(screen.getByRole("button", { name: "No media" }));
    await vi.waitFor(() =>
      expect(getList.mock.calls.at(-1)![0].filters).toEqual([{ field: "media", operator: "eq", value: "without" }]),
    );
    expect(screen.getByRole("button", { name: "Has media" })).toHaveAttribute("aria-pressed", "false");

    // Pressing the active chip again removes the filter.
    await userEvent.click(screen.getByRole("button", { name: "No media" }));
    await vi.waitFor(() => expect(getList.mock.calls.at(-1)![0].filters).toEqual([]));
  });

  it("sends media=wrong and media=to-review from their own chips, one at a time with the other media chips (TASK-80)", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");
    // The media chips sit in the Media menu (TASK-99.2).
    await userEvent.click(screen.getByRole("button", { name: "Media" }));

    await userEvent.click(screen.getByRole("button", { name: "Wrong media" }));
    await vi.waitFor(() =>
      expect(getList.mock.calls.at(-1)![0].filters).toEqual([{ field: "media", operator: "eq", value: "wrong" }]),
    );

    await userEvent.click(screen.getByRole("button", { name: "Media to review" }));
    await vi.waitFor(() =>
      expect(getList.mock.calls.at(-1)![0].filters).toEqual([{ field: "media", operator: "eq", value: "to-review" }]),
    );
    expect(screen.getByRole("button", { name: "Wrong media" })).toHaveAttribute("aria-pressed", "false");

    await userEvent.click(screen.getByRole("button", { name: "Has media" }));
    await vi.waitFor(() =>
      expect(getList.mock.calls.at(-1)![0].filters).toEqual([{ field: "media", operator: "eq", value: "with" }]),
    );
    expect(screen.getByRole("button", { name: "Media to review" })).toHaveAttribute("aria-pressed", "false");
  });

  it("shows a Wrong media badge and an N to review badge in the row, and none for a word with nothing to review", async () => {
    const wrongOne: WordItem = { ...run, needsReview: false, wrongMediaCount: 1, mediaToReviewCount: 2 };
    const plain: WordItem = { ...house, wrongMediaCount: 0, mediaToReviewCount: 0 };
    renderWords([wrongOne, plain]);
    const list = await screen.findByRole("table", { name: "Rows" });
    await within(list).findByText("house");

    expect(within(list).getAllByText("Wrong media")).toHaveLength(1);
    expect(within(list).getByText("2 to review")).toBeInTheDocument();
    // The word-text "Needs review" badge is not used for pictures.
    expect(within(list).queryByText("Needs review")).not.toBeInTheDocument();
  });

  it("shows a reason count with the reasons as a tooltip next to the badge of a flagged word", async () => {
    const flagged: WordItem = { ...run, reviewReasons: ["sense 1: the definition uses hard words [abstain]", "sense 2: x [foo]"] };
    const one: WordItem = { ...run, id: 3, word: "apple", reviewReasons: ["sense 1: y [bar]"] };
    const none: WordItem = { ...run, id: 4, word: "working", reviewReasons: [] };
    renderWords([flagged, one, house, none]);
    const list = await screen.findByRole("table", { name: "Rows" });

    const two = await within(list).findByText("2 problems");
    expect(two).toHaveAttribute("title", "sense 1: the definition uses hard words [abstain]\nsense 2: x [foo]");
    expect(within(list).getByText("1 problem")).toBeInTheDocument();
    expect(within(list).getByText("0 problems")).toHaveAttribute("title", "No problems found now; press ✓ to mark it reviewed");
    // house is reviewed: no count.
    expect(within(list).getAllByText(/problem/)).toHaveLength(3);
  });

  it("mark reviewed posts to the backend, then the list is loaded again without the flag", async () => {
    let flagged = true;
    const getList = vi.fn().mockImplementation(async () => ({ data: [{ ...run, needsReview: flagged }], total: 1 }));
    const custom = vi.fn().mockImplementation(async () => {
      flagged = false;
      return { data: { ...run, needsReview: false } };
    });
    const dataProvider = { getList, custom, getApiUrl: () => "https://api.test/api/admin" } as unknown as DataProvider;
    render(
      <MemoryRouter>
        <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
          <WordsPage />
        </Refine>
      </MemoryRouter>,
    );
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));
    // The word opens in a drawer (a modal), so the list behind it is hidden from the accessibility tree.
    const listRegion = screen.getByRole("region", { name: "List", hidden: true });
    // The filter chip and the badge of the row both say "Needs review".
    expect(within(listRegion).getAllByText("Needs review")).toHaveLength(2);

    await userEvent.click(screen.getByRole("button", { name: "Mark reviewed" }));

    await vi.waitFor(() => expect(within(listRegion).getAllByText("Needs review")).toHaveLength(1));
    expect(custom).toHaveBeenCalledWith(expect.objectContaining({ url: "https://api.test/api/admin/words/1/reviewed", method: "post" }));
    expect(screen.queryByText(/problem/)).not.toBeInTheDocument();
  });

  it("shows the selected word as a table of fields with three icon buttons", async () => {
    renderWords([run]);
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));

    const entry = screen.getByRole("region", { name: "Entry" });
    expect(within(entry).getByText("To move fast on your legs.")).toBeInTheDocument();
    expect(within(entry).getByText("I run every day.")).toBeInTheDocument();
    expect(within(entry).getByText("chạy")).toBeInTheDocument();
    expect(within(entry).getByRole("button", { name: "Mark reviewed" })).toBeInTheDocument();
    expect(within(entry).getByRole("button", { name: "Edit" })).toBeInTheDocument();
    expect(within(entry).getByRole("button", { name: "Regenerate" })).toBeInTheDocument();
  });

  it("shows the photo of a sense with the Pixabay credit, and a dash when there is none", async () => {
    const withPhoto: WordItem = {
      ...run,
      entry: { senses: [run.entry.senses[0], { ...run.entry.senses[0], simpleDefinition: "A race." }] },
      media: [
        { url: "https://api.loffy.me/media/run_1_ab.webp", kind: "photo", photographer: "Jane Photo", pageUrl: "https://pixabay.com/photos/x-1/" },
        null,
      ],
    };
    renderWords([withPhoto]);
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));

    const entry = screen.getByRole("region", { name: "Entry" });
    expect(within(entry).getByRole("img", { name: "run, sense 1" })).toHaveAttribute("src", "https://api.loffy.me/media/run_1_ab.webp");
    expect(within(entry).getByRole("link", { name: "Jane Photo" })).toHaveAttribute("href", "https://pixabay.com/photos/x-1/");
    expect(within(entry).queryByRole("img", { name: "run, sense 2" })).not.toBeInTheDocument();
  });

  it("plays a clip as a muted looping video", async () => {
    const withClip: WordItem = {
      ...run,
      media: [{ url: "https://api.loffy.me/media/run_1_ab.mp4", kind: "clip", photographer: "Pat Maker", pageUrl: "https://pixabay.com/videos/x-2/" }],
    };
    renderWords([withClip]);
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));

    const video = within(screen.getByRole("region", { name: "Entry" })).getByLabelText("run, sense 1");
    expect(video.tagName).toBe("VIDEO");
    expect(video).toHaveAttribute("src", "https://api.loffy.me/media/run_1_ab.mp4");
    expect((video as HTMLVideoElement).muted).toBe(true);
  });

  it("asks for confirmation before regenerating", async () => {
    renderWords([run]);
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));
    await userEvent.click(screen.getByRole("button", { name: "Regenerate" }));
    expect(screen.getByRole("alertdialog")).toHaveTextContent("Delete this entry?");
    await userEvent.click(screen.getByRole("button", { name: "Cancel regenerate" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("edit opens a form with a JSON tab that shows the entry", async () => {
    renderWords([run]);
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));
    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    expect(screen.getByLabelText("Explanation (sense 1)")).toHaveValue("To move fast on your legs.");

    await userEvent.click(screen.getByRole("tab", { name: "JSON" }));
    const json = JSON.parse((screen.getByLabelText("Entry JSON") as HTMLTextAreaElement).value);
    expect(json.senses[0].simpleDefinition).toBe("To move fast on your legs.");
  });

  it("puts each sense in a form section and Cancel before Save at the bottom (TASK-99.4)", async () => {
    renderWords([run]);
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));
    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    const sense = screen.getByRole("region", { name: "Sense 1" });
    expect(within(sense).getByRole("heading", { name: "Sense 1" })).toBeInTheDocument();
    expect(within(sense).getByLabelText("Vietnamese (sense 1)")).toHaveValue("chạy");
    const buttons = screen.getAllByRole("button", { name: /Cancel editing|Save changes/ }).map((b) => b.getAttribute("aria-label"));
    expect(buttons).toEqual(["Cancel editing", "Save changes"]);
  });

  it("shows why the word needs review under Status, and at the top of the edit form", async () => {
    const flagged: WordItem = {
      ...run,
      reviewReasons: ["sense 1: the definition uses hard words [abstain]", "sense 2: the easy example 'x' uses hard words [foo]"],
    };
    renderWords([flagged]);
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));

    const entry = screen.getByRole("region", { name: "Entry" });
    const reasons = within(entry).getByRole("list", { name: "Review reasons" });
    expect(within(reasons).getAllByRole("listitem")).toHaveLength(2);
    expect(within(reasons).getByText("sense 1: the definition uses hard words [abstain]")).toBeInTheDocument();

    await userEvent.click(within(entry).getByRole("button", { name: "Edit" }));
    expect(within(screen.getByRole("region", { name: "Entry" })).getByRole("list", { name: "Review reasons" })).toHaveTextContent("[abstain]");
  });

  it("says there are no problems now when a flagged word has no reasons, and keeps the flag", async () => {
    renderWords([{ ...run, reviewReasons: [] }]);
    await userEvent.click(await screen.findByRole("button", { name: /run/ }));

    const entry = screen.getByRole("region", { name: "Entry" });
    expect(within(entry).getByText("No problems found now; press ✓ to mark it reviewed")).toBeInTheDocument();
    expect(within(entry).getByText("Needs review")).toBeInTheDocument();
    expect(within(entry).queryByRole("list", { name: "Review reasons" })).not.toBeInTheDocument();
  });

  it("shows no reasons for a word that is already reviewed", async () => {
    renderWords([{ ...house, reviewReasons: [] }]);
    await userEvent.click(await screen.findByRole("button", { name: /house/ }));
    expect(screen.queryByText(/No problems found now/)).not.toBeInTheDocument();
  });
});
