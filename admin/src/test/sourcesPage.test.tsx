import { Refine, type DataProvider } from "@refinedev/core";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SourceView } from "@/api/types";
import { SourcesPage } from "@/pages/SourcesPage";
import { httpClient } from "@/providers/dataProvider";

const API = "https://api.test/api/admin";

const upload: SourceView = {
  id: 9,
  title: "Alice",
  status: "ready",
  queuedCount: 0,
  counts: { wordsInFile: 1200, known: 900, newWords: 2, newPhrases: 1, useful: 2, rare: 1, namesSkipped: 1 },
  candidates: [
    { kind: "word", text: "wander", times: 4, zipf: 3.6, snippet: "She liked to wander through the garden", useful: true },
    { kind: "phrase", text: "give up", times: 2, zipf: 5.6, snippet: "He gave up the search", useful: true },
    { kind: "word", text: "ephemeral", times: 1, zipf: 2.5, snippet: "An ephemeral light", useful: false },
    { kind: "name", text: "alice", times: 30, zipf: 0, snippet: "Alice came home", useful: false },
  ],
};

function renderPage() {
  const dataProvider = { getList: vi.fn(), getApiUrl: () => API } as unknown as DataProvider;
  render(
    <MemoryRouter>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <SourcesPage />
      </Refine>
    </MemoryRouter>,
  );
}

const book = new File(["text"], "alice.txt", { type: "text/plain" });

async function uploadBook() {
  await userEvent.upload(screen.getByLabelText("Choose a .txt or .md file"), book);
  await screen.findByRole("table", { name: "Candidates" });
}

describe("Sources page (TASK-108)", () => {
  const post = vi.spyOn(httpClient, "post");

  beforeEach(() => {
    post.mockResolvedValue({ data: upload });
  });
  afterEach(() => vi.clearAllMocks());

  it("shows the drop zone and the title field before an upload", () => {
    renderPage();
    expect(screen.getByText("Drop a .txt or .md file")).toBeInTheDocument();
    expect(screen.getByText("Up to 5 MB. Only the word list is kept, never the text.")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Candidates" })).not.toBeInTheDocument();
  });

  it("sends the file and the title as a form with the upload header", async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText("Title"), "Alice in Wonderland");
    await uploadBook();

    const [url, body, config] = post.mock.calls[0];
    expect(url).toBe(`${API}/sources`);
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get("file")).toBe(book);
    expect((body as FormData).get("title")).toBe("Alice in Wonderland");
    expect(config).toEqual({ headers: { "X-Requested-With": "XMLHttpRequest" } });
  });

  it("uses the file name as the title when the field is empty", async () => {
    renderPage();
    await uploadBook();
    expect(((post.mock.calls[0][1]) as FormData).get("title")).toBe("alice");
  });

  it("accepts a dropped file", async () => {
    renderPage();
    fireEvent.drop(screen.getByRole("group", { name: "Upload a book" }), { dataTransfer: { files: [book] } });
    await screen.findByRole("table", { name: "Candidates" });
    expect(post).toHaveBeenCalledTimes(1);
  });

  it("shows the four counts and the filter chips with their counts", async () => {
    renderPage();
    await uploadBook();

    for (const [label, value] of [["Words in file", "1,200"], ["Known", "900"], ["New words", "2"], ["New phrases", "1"]]) {
      expect(screen.getByText(label).parentElement).toHaveTextContent(value);
    }
    expect(screen.getByRole("button", { name: /^Useful\s*2$/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /^Rare\s*1$/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Names skipped\s*1$/ })).toBeInTheDocument();
  });

  it("lists the useful words with a phrase badge, times, score and snippet; the chips switch the rows", async () => {
    renderPage();
    await uploadBook();

    const table = screen.getByRole("table", { name: "Candidates" });
    const phraseRow = within(table).getByText("give up").closest("tr")!;
    expect(within(phraseRow).getByText("phrase")).toBeInTheDocument();
    expect(phraseRow).toHaveTextContent("5.6");
    expect(phraseRow).toHaveTextContent("He gave up the search");
    expect(within(table).queryByText("ephemeral")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /^Rare/ }));
    expect(within(table).getByText("ephemeral")).toBeInTheDocument();
    expect(within(table).queryByText("wander")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /^Names skipped/ }));
    expect(within(table).getByText("alice")).toBeInTheDocument();
    expect(within(table).queryByRole("checkbox", { name: "Pick alice" })).not.toBeInTheDocument();
  });

  it("counts the picked items and adds them to the queue", async () => {
    renderPage();
    await uploadBook();
    expect(screen.getByText("0 picked")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add 0 to queue" })).toBeDisabled();

    await userEvent.click(screen.getByRole("checkbox", { name: "Pick wander" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Pick give up" }));
    expect(screen.getByText("2 picked")).toBeInTheDocument();

    post.mockResolvedValueOnce({ data: { queued: 1, alreadyQueued: 1, source: "book:Alice" } });
    await userEvent.click(screen.getByRole("button", { name: "Add 2 to queue" }));

    const [url, body, config] = post.mock.calls[1];
    expect(url).toBe(`${API}/sources/9/queue`);
    expect(body).toEqual({ items: [{ kind: "word", text: "wander" }, { kind: "phrase", text: "give up" }] });
    expect(config).toEqual({ headers: { "Content-Type": "application/json" } });
    expect(await screen.findByRole("status")).toHaveTextContent("Added 1 to the queue (1 were already there)");
    expect(screen.getByText("0 picked")).toBeInTheDocument();
    // Queued: the picks are locked and the snippets are gone.
    expect(screen.getByRole("checkbox", { name: "Pick wander" })).toBeDisabled();
    expect(screen.queryByText("He gave up the search")).not.toBeInTheDocument();
  });

  it("picks every shown row with the header checkbox", async () => {
    renderPage();
    await uploadBook();
    await userEvent.click(screen.getByRole("checkbox", { name: "Pick all shown" }));
    expect(screen.getByText("2 picked")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("checkbox", { name: "Pick all shown" }));
    expect(screen.getByText("0 picked")).toBeInTheDocument();
  });

  it("shows the message of the backend when the upload fails", async () => {
    post.mockRejectedValue({ response: { status: 400, data: { detail: "Upload a .txt or .md file." } } });
    renderPage();
    await userEvent.upload(screen.getByLabelText("Choose a .txt or .md file"), new File(["x"], "a.pdf"), {
      applyAccept: false,
    });
    expect(await screen.findByRole("alert")).toHaveTextContent("Upload a .txt or .md file.");
  });

  it("says the file is too big when the answer is 413 without details", async () => {
    post.mockRejectedValue({ response: { status: 413, data: "" } });
    renderPage();
    await userEvent.upload(screen.getByLabelText("Choose a .txt or .md file"), book);
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("That file is too big. The limit is 5 MB.");
  });
});
