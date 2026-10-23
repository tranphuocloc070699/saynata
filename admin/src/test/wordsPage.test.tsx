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
  const getList = vi.fn().mockImplementation(async ({ filters, resource }) => {
    // The Pending count asks the prefill queue: empty here.
    if (resource !== "words") return { data: [], total: 0 };
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

// The list requests only (50 rows); the Filter menu's counts ask for one row.
function listCalls(getList: ReturnType<typeof vi.fn>) {
  return getList.mock.calls.map((c) => c[0]).filter((p) => p.pagination?.pageSize === 50);
}

// Opens the Filter menu if needed and clicks one choice (TASK-101).
async function choose(label: string) {
  if (!screen.queryByRole("group", { name: "Filter choices" })) {
    await userEvent.click(screen.getByRole("button", { name: /^Filter/ }));
  }
  await userEvent.click(within(screen.getByRole("group", { name: "Filter choices" })).getByRole("button", { name: label }));
}

describe("words page", () => {
  it("starts with the search from the top bar (?q=)", async () => {
    const getList = renderWords([run, house], "/words?q=run");
    expect(await screen.findByRole("searchbox", { name: "Search" })).toHaveValue("run");
    expect(getList.mock.calls[0][0].filters).toContainEqual({ field: "q", operator: "contains", value: "run" });
  });

  it("lists the words with a Needs review · N badge", async () => {
    renderWords([run, house]);
    const list = await screen.findByRole("table", { name: "Rows" });
    expect(await within(list).findByText("run")).toBeInTheDocument();
    expect(within(list).getByText("house")).toBeInTheDocument();
    expect(within(list).getByText("Needs review · 0")).toBeInTheDocument();
  });

  it("shows the words as a table with Level, Source and Status, and no badge for a word that is fine (TASK-101)", async () => {
    renderWords([run, house]);
    const table = await screen.findByRole("table", { name: "Rows" });
    expect(within(table).getAllByRole("columnheader").map((th) => th.textContent)).toEqual(["Word", "Level", "Source", "Status"]);
    const houseRow = (await within(table).findByText("house")).closest("tr")!;
    expect(within(houseRow).getByText("Bundled")).toBeInTheDocument();
    expect(within(houseRow).getByText("A1")).toBeInTheDocument();
    expect(within(houseRow).queryByText(/Reviewed|No media|review/)).not.toBeInTheDocument();
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

  it("groups the Filter choices under Level, Source and Status, each with its count", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");
    await userEvent.click(screen.getByRole("button", { name: /^Filter/ }));
    const menu = screen.getByRole("group", { name: "Filter choices" });
    expect(within(menu).getByText("Level")).toBeInTheDocument();
    expect(within(menu).getByText("Source")).toBeInTheDocument();
    expect(within(menu).getByText("Status")).toBeInTheDocument();
    expect(within(menu).getByText("On the phone, inside the app. Works offline.")).toBeInTheDocument();

    // Each count is the list endpoint with one row asked and that choice alone.
    const needsReview = within(menu).getByRole("button", { name: "Needs review" });
    await vi.waitFor(() => expect(needsReview).toHaveTextContent(/1$/));
    const countCall = getList.mock.calls.find(
      (c) => c[0].pagination?.pageSize === 1 && c[0].filters?.[0]?.field === "needs_review",
    );
    expect(countCall![0].filters).toEqual([{ field: "needs_review", operator: "eq", value: true }]);
    expect(within(menu).getAllByRole("button").map((b) => b.getAttribute("aria-label"))).toEqual([
      "A1-A2",
      "B1-B2",
      "C1-C2",
      "Bundled",
      "Backend database",
      "Pending",
      "Needs review",
    ]);
  });

  it("asks the backend for needs_review=true when the choice is on, and the tag removes it", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");

    await choose("Needs review");

    await vi.waitFor(() => expect(screen.queryByText("house")).not.toBeInTheDocument());
    expect(listCalls(getList).at(-1)!.filters).toEqual([{ field: "needs_review", operator: "eq", value: true }]);
    expect(screen.getByText("run")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Remove filter Needs review" }));
    expect(await screen.findByText("house")).toBeInTheDocument();
  });

  it("sends the source choice to the backend and shows only what the backend returns", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");
    getList.mockClear();

    await choose("Bundled");

    await vi.waitFor(() => expect(screen.queryByText("run")).not.toBeInTheDocument());
    expect(screen.getAllByText("house").length).toBeGreaterThan(0);
    expect(listCalls(getList)).toHaveLength(1);
    expect(listCalls(getList)[0].filters).toEqual([{ field: "source", operator: "eq", value: "bundled" }]);
  });

  it("Bundled and Backend database work one at a time", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");

    await choose("Bundled");
    await choose("Backend database");

    await vi.waitFor(() => expect(screen.queryByText("house")).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Bundled" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Backend database" })).toHaveAttribute("aria-pressed", "true");
    expect(listCalls(getList).at(-1)!.filters).toEqual([{ field: "source", operator: "eq", value: "backend" }]);
  });

  it("OR-s the level choices into one cefr list and combines them with the source", async () => {
    const getList = renderWords([run, house]);
    await screen.findByText("house");

    await choose("A1-A2");
    await choose("C1-C2");
    await choose("Bundled");

    await vi.waitFor(() =>
      expect(listCalls(getList).at(-1)!.filters).toEqual([
        { field: "cefr", operator: "in", value: ["A1", "A2", "C1", "C2"] },
        { field: "source", operator: "eq", value: "bundled" },
      ]),
    );
    // Turning a level off leaves the other one.
    await choose("A1-A2");
    await vi.waitFor(() =>
      expect(listCalls(getList).at(-1)!.filters).toContainEqual({ field: "cefr", operator: "in", value: ["C1", "C2"] }),
    );
  });

  it("shows the reasons of a flagged word as the tooltip of its badge", async () => {
    const flagged: WordItem = { ...run, reviewReasons: ["sense 1: the definition uses hard words [abstain]", "sense 2: x [foo]"] };
    const none: WordItem = { ...run, id: 4, word: "working", reviewReasons: [] };
    renderWords([flagged, house, none]);
    const list = await screen.findByRole("table", { name: "Rows" });

    const two = await within(list).findByText("Needs review · 2");
    expect(two).toHaveAttribute("title", "sense 1: the definition uses hard words [abstain]\nsense 2: x [foo]");
    expect(within(list).getByText("Needs review · 0")).toHaveAttribute("title", "No problems found now; press ✓ to mark it reviewed");
    // house is reviewed: no badge.
    expect(within(list).getAllByText(/Needs review/)).toHaveLength(2);
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
    expect(within(listRegion).getByText("Needs review · 0")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Mark reviewed" }));

    await vi.waitFor(() => expect(within(listRegion).queryByText(/Needs review/)).not.toBeInTheDocument());
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
