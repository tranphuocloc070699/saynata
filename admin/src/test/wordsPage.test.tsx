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

function renderWords(words: WordItem[]) {
  const getList = vi.fn().mockImplementation(async ({ filters }) => {
    const onlyReview = filters?.some((f: { field: string }) => f.field === "needs_review");
    const data = onlyReview ? words.filter((w) => w.needsReview) : words;
    return { data, total: data.length };
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

describe("words page", () => {
  it("lists the words with a needs review badge", async () => {
    renderWords([run, house]);
    const list = await screen.findByRole("list");
    expect(await within(list).findByText("run")).toBeInTheDocument();
    expect(within(list).getByText("house")).toBeInTheDocument();
    expect(within(list).getByText("Needs review")).toBeInTheDocument();
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

  it("filters by source on the loaded page", async () => {
    renderWords([run, house]);
    await screen.findByText("house");
    await userEvent.click(screen.getByRole("button", { name: "Bundled" }));
    expect(screen.queryByText("run")).not.toBeInTheDocument();
    expect(screen.getByText("house")).toBeInTheDocument();
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
