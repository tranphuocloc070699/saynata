import { Refine, type DataProvider } from "@refinedev/core";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from "vitest";
import type { AiJob, AiPrompt, WordItem } from "@/api/types";
import { AI_POLL_MS, MediaRow } from "@/components/MediaRow";
import { httpClient } from "@/providers/dataProvider";

const API = "https://api.test/api/admin";
const SENSE = `${API}/words/b-5/senses/0/media`;

const word: WordItem = {
  id: "b-5",
  word: "run",
  needsReview: false,
  source: "bundled",
  model: "haiku",
  promptVersion: "v3",
  createdAt: "2026-10-14T10:00:00Z",
  entry: {
    senses: [
      { pos: "verb", cefr: "A1", simpleDefinition: "to move fast on your feet", vietnamese: "chay", examples: [], usage: [] },
    ],
  },
  media: [{ url: "https://api.loffy.me/media/run_1_ab.webp", kind: "photo", photographer: "Jane Photo", pageUrl: "https://pixabay.com/photos/x-1/" }],
  mediaStatus: [null],
};

const usage = { picturesToday: 3, picturesPerDay: 30, sceneUsdToday: 0.04, sceneUsdPerDay: 1 };
const templatePrompt: AiPrompt = { prompt: "TEMPLATE PROMPT", source: "template", usage };

function job(status: AiJob["status"], error: string | null = null): AiJob {
  return { id: 7, status, error, createdAt: "2026-10-18T10:00:00Z", finishedAt: null };
}

function renderRow(item: WordItem = word) {
  const dataProvider = { getList: vi.fn(), getApiUrl: () => API } as unknown as DataProvider;
  render(
    <MemoryRouter>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <MediaRow word={item} index={0} />
      </Refine>
    </MemoryRouter>,
  );
}

// The prompt endpoint answers GET .../ai-prompt; the job endpoint answers GET .../ai-picture.
function answerGets(prompt: AiPrompt, latest: () => AiJob | null = () => null) {
  vi.spyOn(httpClient, "get").mockImplementation(async (url: string) => {
    if (url === `${SENSE}/ai-prompt`) return { data: prompt, status: 200 };
    if (url === `${SENSE}/ai-picture`) {
      const current = latest();
      return current ? { data: current, status: 200 } : { data: "", status: 204 };
    }
    return { data: {}, status: 200 };
  });
}

async function openDialog() {
  await userEvent.click(screen.getByRole("button", { name: "Draw an AI picture" }));
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findByDisplayValue(/PROMPT|SCENE|scene/i);
  return dialog;
}

// TASK-91: the Sparkles button opens a dialog with the prompt; OK starts a job on the server.
describe("AI picture dialog", () => {
  let post: MockInstance;

  beforeEach(() => {
    post = vi.spyOn(httpClient, "post").mockResolvedValue({ data: job("queued"), status: 202 });
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("opens with the fetched prompt, the current picture, the meaning and the limits of the day", async () => {
    answerGets(templatePrompt);
    renderRow();

    const dialog = await openDialog();

    expect(within(dialog).getByText("AI picture")).toBeInTheDocument();
    expect(within(dialog).getByText("run, sense 1 (verb)")).toBeInTheDocument();
    expect(within(dialog).getByText("to move fast on your feet")).toBeInTheDocument();
    expect(within(dialog).getByRole("img", { name: "Current picture" })).toHaveAttribute("src", "https://api.loffy.me/media/run_1_ab.webp");
    expect(within(dialog).getByLabelText("Prompt")).toHaveValue("TEMPLATE PROMPT");
    expect(within(dialog).getByText("Prompt from: template")).toBeInTheDocument();
    expect(within(dialog).getByText("today 3 of 30 pictures · $0.04 of $1.00")).toBeInTheDocument();
    expect(httpClient.get).toHaveBeenCalledWith(`${SENSE}/ai-prompt`);
  });

  it("names the last media-ai scene as the source of the prompt", async () => {
    answerGets({ prompt: "SCENE PROMPT", source: "scene", sceneVersion: "ai-v1", usage });
    renderRow();

    const dialog = await openDialog();

    expect(within(dialog).getByText("Prompt from: last media-ai scene (ai-v1)")).toBeInTheDocument();
  });

  it("has only icon buttons, each with an accessible name, and the wand says about $0.02", async () => {
    answerGets(templatePrompt);
    renderRow();
    const dialog = await openDialog();

    for (const name of ["Close", "Put back the first prompt", "Write a new scene with Claude", "Cancel", "Draw the picture"]) {
      const button = within(dialog).getByRole("button", { name });
      expect(button).toHaveTextContent("");
      expect(button.querySelector("svg")).not.toBeNull();
    }
    expect(within(dialog).getByRole("button", { name: "Write a new scene with Claude" })).toHaveAttribute("title", expect.stringContaining("about $0.02"));
  });

  it("an empty prompt shows an inline error and sends nothing", async () => {
    answerGets(templatePrompt);
    renderRow();
    const dialog = await openDialog();

    await userEvent.clear(within(dialog).getByLabelText("Prompt"));
    await userEvent.click(within(dialog).getByRole("button", { name: "Draw the picture" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Write a prompt first.");
    expect(post).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("a prompt over 4000 characters is refused in the dialog", async () => {
    answerGets(templatePrompt);
    renderRow();
    const dialog = await openDialog();

    const field = within(dialog).getByLabelText("Prompt");
    await userEvent.clear(field);
    await userEvent.click(field);
    await userEvent.paste("x".repeat(4001));
    await userEvent.click(within(dialog).getByRole("button", { name: "Draw the picture" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("4000 characters");
    expect(post).not.toHaveBeenCalled();
  });

  it("the wand asks Claude for a new scene and puts it in the box; undo puts the first prompt back", async () => {
    answerGets(templatePrompt);
    post.mockResolvedValue({
      data: { prompt: "CLAUDE SCENE", source: "claude", usage: { ...usage, sceneUsdToday: 0.06 } },
      status: 200,
    });
    renderRow();
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Write a new scene with Claude" }));

    await vi.waitFor(() => expect(within(dialog).getByLabelText("Prompt")).toHaveValue("CLAUDE SCENE"));
    expect(post).toHaveBeenCalledWith(`${SENSE}/ai-prompt/scene`, {}, { headers: { "Content-Type": "application/json" } });
    expect(within(dialog).getByText("Prompt from: new Claude scene")).toBeInTheDocument();
    expect(within(dialog).getByText("today 3 of 30 pictures · $0.06 of $1.00")).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Put back the first prompt" }));

    expect(within(dialog).getByLabelText("Prompt")).toHaveValue("TEMPLATE PROMPT");
    expect(within(dialog).getByText("Prompt from: template")).toBeInTheDocument();
  });

  it("shows the message of the backend when the wand fails", async () => {
    answerGets(templatePrompt);
    post.mockRejectedValue({ response: { status: 422, data: { detail: "Claude says this meaning cannot be drawn." } } });
    renderRow();
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Write a new scene with Claude" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Claude says this meaning cannot be drawn.");
    expect(within(dialog).getByLabelText("Prompt")).toHaveValue("TEMPLATE PROMPT");
  });

  it("OK sends the prompt, closes the dialog and the row shows the drawing state", async () => {
    answerGets(templatePrompt);
    renderRow();
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Draw the picture" }));

    await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post).toHaveBeenCalledWith(
      `${SENSE}/ai-picture`,
      { prompt: "TEMPLATE PROMPT", source: "template" },
      { headers: { "Content-Type": "application/json" } },
    );
    await vi.waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getByRole("status")).toHaveTextContent("Drawing an AI picture… you can leave this page");
    expect(screen.getByRole("button", { name: "Draw an AI picture" })).toBeDisabled();
  });

  it("an edited prompt is sent with the source edited", async () => {
    answerGets(templatePrompt);
    renderRow();
    const dialog = await openDialog();

    await userEvent.type(within(dialog).getByLabelText("Prompt"), " Add a small dog.");
    await userEvent.click(within(dialog).getByRole("button", { name: "Draw the picture" }));

    await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][1]).toEqual({ prompt: "TEMPLATE PROMPT Add a small dog.", source: "edited" });
  });

  it("keeps the dialog open and shows the reason when the server refuses (409, 429, 503)", async () => {
    answerGets(templatePrompt);
    post.mockRejectedValue({ response: { status: 429, data: { detail: "Today's limit of 30 AI pictures is used up. Try again tomorrow." } } });
    renderRow();
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Draw the picture" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Today's limit of 30 AI pictures is used up.");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Draw the picture" })).toBeEnabled();
  });

  it("Cancel closes the dialog without sending", async () => {
    answerGets(templatePrompt);
    renderRow();
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

    await vi.waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(post).not.toHaveBeenCalled();
  });
});

// The row asks the server every 3 seconds while a job is queued or drawing, and the state comes from the server.
describe("AI picture job in the media row", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  async function tick() {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(AI_POLL_MS + 50);
    });
  }

  it("a word that is already drawing (page reopened) shows the placeholder and keeps asking", async () => {
    let status: AiJob["status"] = "drawing";
    answerGets(templatePrompt, () => job(status));
    renderRow({ ...word, mediaStatus: ["ai_drawing"] });

    expect(screen.getByRole("status")).toHaveTextContent("Drawing an AI picture… you can leave this page");
    expect(screen.queryByRole("img", { name: "run, sense 1" })).not.toBeInTheDocument();
    expect(httpClient.get).not.toHaveBeenCalled(); // nothing before the first 3 seconds

    await tick();
    expect(httpClient.get).toHaveBeenCalledWith(`${SENSE}/ai-picture`, expect.anything());
    expect(screen.getByRole("status")).toBeInTheDocument();

    status = "done";
    await tick();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    // It stops asking once the job is done.
    const calls = vi.mocked(httpClient.get).mock.calls.length;
    await tick();
    expect(vi.mocked(httpClient.get).mock.calls.length).toBe(calls);
  });

  it("a finished job brings the new picture: the caption is the AI credit", async () => {
    answerGets(templatePrompt, () => job("done"));
    const drawingWord = { ...word, mediaStatus: ["ai_drawing"] };
    renderRow(drawingWord);
    await tick();

    // The word list is read again by Refine; here the same row gets the new word.
    const aiWord: WordItem = {
      ...word,
      media: [{ url: "https://api.loffy.me/media/run_1_ai_12345678.webp", kind: "photo", photographer: null, pageUrl: null, source: "ai", licenseNote: "AI image (codex)" }],
      mediaStatus: ["ai_generated"],
    };
    // Render a second row with the new word to see its caption.
    renderRow(aiWord);
    expect(screen.getByText("AI image (codex)")).toBeInTheDocument();
  });

  it("a failed job shows a red line with the error and keeps the old picture", async () => {
    answerGets(templatePrompt, () => job("failed", "The ChatGPT plan limit is reached. Try again later."));
    renderRow({ ...word, mediaStatus: ["ai_drawing"] });

    await tick();

    expect(await screen.findByRole("alert")).toHaveTextContent("The ChatGPT plan limit is reached. Try again later.");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: "run, sense 1" })).toHaveAttribute("src", "https://api.loffy.me/media/run_1_ab.webp");
    expect(screen.getByRole("button", { name: "Draw an AI picture" })).toBeEnabled();
  });

  it("a short network problem while asking does not stop the polling", async () => {
    let calls = 0;
    vi.spyOn(httpClient, "get").mockImplementation(async () => {
      calls += 1;
      if (calls === 1) throw new Error("Network Error");
      return { data: job("failed", "x"), status: 200 };
    });
    renderRow({ ...word, mediaStatus: ["ai_drawing"] });

    await tick();
    expect(screen.getByRole("status")).toBeInTheDocument();
    await tick();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
