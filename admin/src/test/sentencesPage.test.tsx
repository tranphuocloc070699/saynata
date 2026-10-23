import { Refine, type DataProvider } from "@refinedev/core";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import type { SentenceItem } from "@/api/types";
import { SentencesPage } from "@/pages/SentencesPage";

const looksGood: SentenceItem = {
  id: 1,
  sourceText: "Looks good.",
  targetLang: "vi",
  needsReview: true,
  model: "deepseek-v4.1-flash",
  promptVersion: "s3",
  createdAt: "2026-10-20T10:00:00Z",
  explanation: { meaning: "It seems fine.", words: [{ word: "good", partOfSpeech: "adj", meaning: "fine" }], translation: "Trông ổn." },
};
const hand: SentenceItem = { ...looksGood, id: 2, sourceText: "Give me a hand.", needsReview: false, explanation: { ...looksGood.explanation, meaning: "Help me." } };

function renderSentences() {
  const getList = vi.fn().mockResolvedValue({ data: [looksGood, hand], total: 2 });
  const dataProvider = { getList, getApiUrl: () => "https://api.test/api/admin" } as unknown as DataProvider;
  render(
    <MemoryRouter>
      <Refine dataProvider={dataProvider} options={{ disableTelemetry: true }}>
        <SentencesPage />
      </Refine>
    </MemoryRouter>,
  );
}

describe("sentences page (TASK-99.3)", () => {
  it("shows the sentences as a table with Language, Review and Model columns", async () => {
    renderSentences();
    const table = await screen.findByRole("table", { name: "Rows" });
    expect(within(table).getAllByRole("columnheader").map((th) => th.textContent)).toEqual(["Sentence", "Language", "Review", "Model"]);
    const first = (await within(table).findByText("Looks good.")).closest("tr")!;
    expect(within(first).getByText("It seems fine.")).toBeInTheDocument();
    expect(within(first).getByText("Needs review")).toBeInTheDocument();
    expect(within((within(table).getByText("Give me a hand.")).closest("tr")!).getByText("Reviewed")).toBeInTheDocument();
  });

  it("opens a sentence in the drawer with the simple explanation before the translation", async () => {
    renderSentences();
    await userEvent.click((await screen.findByText("Help me.")).closest("tr")!);
    const entry = screen.getByRole("region", { name: "Entry" });
    expect(within(entry).getByRole("heading", { name: "Give me a hand." })).toBeInTheDocument();
    const text = entry.textContent ?? "";
    expect(text.indexOf("Help me.")).toBeLessThan(text.indexOf("Trông ổn."));
  });
});
