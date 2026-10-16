import { Refine, type DataProvider } from "@refinedev/core";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { WordItem } from "@/api/types";
import { MediaRow } from "@/components/MediaRow";
import { httpClient } from "@/providers/dataProvider";

const API = "https://api.test/api/admin";

const word: WordItem = {
  id: "b-5",
  word: "run",
  needsReview: false,
  source: "bundled",
  model: "haiku",
  promptVersion: "v3",
  createdAt: "2026-10-14T10:00:00Z",
  entry: { senses: [] },
  media: [{ url: "https://api.loffy.me/media/run_1_ab.webp", kind: "photo", photographer: "Jane Photo", pageUrl: "https://pixabay.com/photos/x-1/" }],
  mediaStatus: [null],
};

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

// The element that takes the drops: the outer div of the Media row.
function dropZone() {
  return screen.getByRole("group", { name: "Media actions (sense 1)" }).parentElement!.parentElement!;
}

// TASK-79: the admin uploads their own picture or clip for a sense.
describe("upload from my computer", () => {
  const post = vi.spyOn(httpClient, "post");
  const picture = new File(["png-bytes"], "chatgpt.png", { type: "image/png" });

  beforeEach(() => {
    post.mockResolvedValue({ data: {} });
  });
  afterEach(() => vi.clearAllMocks());

  it("has a fourth icon-only Upload button next to the three others", () => {
    renderRow();
    const button = screen.getByRole("button", { name: "Upload a picture or clip" });
    expect(button).toHaveTextContent("");
    expect(button.querySelector("svg")).not.toBeNull();
    expect(button).toBeEnabled();
    for (const name of ["Pick another image", "No image", "Mark for AI image"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
  });

  it("the Upload button opens the file picker for pictures and videos", async () => {
    renderRow();
    const input = screen.getByLabelText("Choose a picture or clip") as HTMLInputElement;
    expect(input).toHaveAttribute("accept", "image/*,video/*");
    const open = vi.spyOn(input, "click");

    await userEvent.click(screen.getByRole("button", { name: "Upload a picture or clip" }));

    expect(open).toHaveBeenCalledTimes(1);
  });

  it("sends the chosen file as FormData with the form-upload header", async () => {
    renderRow();
    await userEvent.upload(screen.getByLabelText("Choose a picture or clip"), picture);

    await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    const [url, body, config] = post.mock.calls[0];
    expect(url).toBe(`${API}/words/b-5/senses/0/media/upload`);
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get("file")).toBe(picture);
    // No Content-Type here: the browser adds multipart/form-data with its boundary.
    expect(config).toEqual({ headers: { "X-Requested-With": "XMLHttpRequest" } });
  });

  it("shows a spinner and disables the buttons while uploading, then goes back", async () => {
    let finish!: () => void;
    post.mockReturnValue(new Promise((resolve) => (finish = () => resolve({ data: {} }))));
    renderRow();

    await userEvent.upload(screen.getByLabelText("Choose a picture or clip"), picture);

    expect(await screen.findByRole("status", { name: "Uploading" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upload a picture or clip" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "No image" })).toBeDisabled();

    finish();
    await vi.waitFor(() => expect(screen.queryByRole("status", { name: "Uploading" })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Upload a picture or clip" })).toBeEnabled();
  });

  it("shows the message of the backend when the upload fails, and the row stays usable", async () => {
    post.mockRejectedValue({ response: { status: 400, data: { detail: "That file is not a picture or a short video." } } });
    renderRow();

    await userEvent.upload(screen.getByLabelText("Choose a picture or clip"), new File(["x"], "notes.txt", { type: "text/plain" }), {
      applyAccept: false,
    });

    expect(await screen.findByRole("alert")).toHaveTextContent("That file is not a picture or a short video.");
    expect(screen.getByRole("button", { name: "Upload a picture or clip" })).toBeEnabled();
  });

  it("says the file is too big when the answer is 413 without details", async () => {
    post.mockRejectedValue({ response: { status: 413, data: "" } });
    renderRow();
    await userEvent.upload(screen.getByLabelText("Choose a picture or clip"), picture);
    expect(await screen.findByRole("alert")).toHaveTextContent("That file is too big.");
  });

  it("a dropped file is uploaded, and a dashed outline shows while dragging", async () => {
    renderRow();
    const row = dropZone();
    expect(row.className).not.toContain("outline-dashed");

    fireEvent.dragEnter(row, { dataTransfer: { types: ["Files"] } });
    expect(row.className).toContain("outline-dashed");
    fireEvent.dragLeave(row, { dataTransfer: { types: ["Files"] } });
    expect(row.className).not.toContain("outline-dashed");

    fireEvent.dragOver(row, { dataTransfer: { types: ["Files"] } });
    expect(row.className).toContain("outline-dashed");
    fireEvent.drop(row, { dataTransfer: { types: ["Files"], files: [picture] } });

    expect(row.className).not.toContain("outline-dashed");
    await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe(`${API}/words/b-5/senses/0/media/upload`);
    expect((post.mock.calls[0][1] as FormData).get("file")).toBe(picture);
  });

  it("dragged text (not a file) is ignored", () => {
    renderRow();
    const row = dropZone();
    fireEvent.dragEnter(row, { dataTransfer: { types: ["text/plain"] } });
    expect(row.className).not.toContain("outline-dashed");
    fireEvent.drop(row, { dataTransfer: { types: ["text/plain"], files: [] } });
    expect(post).not.toHaveBeenCalled();
  });

  it("shows the caption 'Uploaded by the admin' and the viewer works for an uploaded file", async () => {
    renderRow({
      ...word,
      media: [
        {
          url: "https://api.loffy.me/media/run_1_cd.webp",
          kind: "photo",
          photographer: null,
          pageUrl: null,
          licenseNote: "Uploaded by the admin",
          source: "upload",
        },
      ],
    });
    expect(screen.getByText("Uploaded by the admin")).toBeInTheDocument();
    expect(screen.queryByText(/on Pixabay/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "View run, sense 1 full size" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("img", { name: "run · sense 1" })).toHaveAttribute("src", "https://api.loffy.me/media/run_1_cd.webp");
    expect(dialog).toHaveTextContent("run · sense 1 · Uploaded by the admin");
    expect(within(dialog).queryByRole("link")).not.toBeInTheDocument();
  });
});
