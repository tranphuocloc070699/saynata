import type { Example, ExampleLevel, Sense, UsageLine, WordEntry, WordItem } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Input, Textarea } from "@/components/ui/input";
import { Field } from "@/components/EntryEditor";
import { MediaRow } from "@/components/MediaRow";
import { EntryBrowser, type Chip } from "@/components/EntryBrowser";
import type { FieldRow } from "@/components/FieldTable";

const LEVELS: ExampleLevel[] = ["easy", "medium", "harder"];

const cefrChip = (label: string, levels: string[]): Chip<WordItem> => ({
  id: label,
  label,
  client: { group: "cefr", matches: (word) => word.entry.senses.some((sense) => levels.includes(sense.cefr)) },
});

const chips: Chip<WordItem>[] = [
  { id: "needs-review", label: "Needs review", server: { field: "needs_review", value: true } },
  { id: "bundled", label: "Bundled", client: { group: "source", matches: (word) => word.source === "bundled" } },
  { id: "backend", label: "From backend", client: { group: "source", matches: (word) => word.source === "backend" } },
  cefrChip("A1-A2", ["A1", "A2"]),
  cefrChip("B1-B2", ["B1", "B2"]),
];

// Why a word needs review: the lines from the backend, or a hint when the entry is fine now.
// The flag is never cleared here. The developer decides, with the check button.
function ReviewReasons({ word }: { word: WordItem }) {
  if (!word.needsReview) return null;
  const reasons = word.reviewReasons ?? [];
  if (reasons.length === 0) {
    return <p className="text-sm text-muted-foreground">No problems found now; press ✓ to mark it reviewed</p>;
  }
  return (
    <ul aria-label="Review reasons" className="list-disc space-y-1 pl-5 text-sm">
      {reasons.map((reason) => (
        <li key={reason}>{reason}</li>
      ))}
    </ul>
  );
}

// Same order as the app: explanation, examples, usage, then Vietnamese last.
function detailRows(word: WordItem): FieldRow[] {
  const rows: FieldRow[] = [
    { label: "Word", value: word.word },
    { label: "IPA", value: word.ipa ?? "-" },
    { label: "Source", value: word.source === "bundled" ? "Bundled in the app" : "From the backend" },
    {
      label: "Status",
      value: word.needsReview ? (
        <div className="space-y-2">
          <Badge variant="warning">Needs review</Badge>
          <ReviewReasons word={word} />
        </div>
      ) : (
        "Reviewed"
      ),
    },
  ];
  word.entry.senses.forEach((sense, index) => {
    rows.push({ label: `Sense ${index + 1}`, heading: true, value: null });
    rows.push({ label: "Part of speech", value: `${sense.pos} · ${sense.cefr}` });
    rows.push({ label: "Explanation", value: sense.simpleDefinition });
    for (const level of LEVELS) {
      const example = sense.examples.find((e) => e.level === level);
      rows.push({ label: `Example (${level})`, value: example?.text ?? "-" });
    }
    rows.push({
      label: "Usage",
      value: sense.usage.length ? (
        <ul className="space-y-1">
          {sense.usage.map((line, i) => (
            <li key={i}>
              <span className="text-xs text-muted-foreground">{line.kind}</span> <strong>{line.pattern}</strong>
              <div className="text-muted-foreground">{line.example}</div>
            </li>
          ))}
        </ul>
      ) : (
        "-"
      ),
    });
    rows.push({ label: "Vietnamese", value: sense.vietnamese });
    rows.push({
      label: "Media",
      value: <MediaRow word={word} index={index} />,
    });
  });
  rows.push({ label: "Record", heading: true, value: null });
  rows.push({ label: "Model", value: `${word.model} (prompt ${word.promptVersion})` });
  rows.push({ label: "Created", value: word.createdAt });
  return rows;
}

function WordForm({ draft, setDraft, word }: { draft: WordEntry; setDraft: (next: WordEntry) => void; word: WordItem }) {
  const changeSense = (index: number, patch: Partial<Sense>) =>
    setDraft({ senses: draft.senses.map((s, i) => (i === index ? { ...s, ...patch } : s)) });

  const changeExample = (senseIndex: number, level: ExampleLevel, text: string) => {
    const sense = draft.senses[senseIndex];
    const others = sense.examples.filter((e) => e.level !== level);
    const examples: Example[] = LEVELS.map((l) => (l === level ? { level, text } : others.find((e) => e.level === l))).filter(
      (e): e is Example => !!e,
    );
    changeSense(senseIndex, { examples });
  };

  const changeUsage = (senseIndex: number, lineIndex: number, patch: Partial<UsageLine>) =>
    changeSense(senseIndex, {
      usage: draft.senses[senseIndex].usage.map((u, i) => (i === lineIndex ? { ...u, ...patch } : u)),
    });

  return (
    <div className="space-y-6">
      {word.needsReview && (
        <div className="space-y-2 rounded-md border border-border bg-card p-4">
          <Badge variant="warning">Needs review</Badge>
          <ReviewReasons word={word} />
        </div>
      )}
      <p className="text-xs text-muted-foreground">IPA is shown in the table but is not changed here (the backend saves only the entry).</p>
      {draft.senses.map((sense, i) => (
        <fieldset key={i} className="space-y-3 rounded-md border border-border bg-card p-4">
          <legend className="px-1 text-xs font-semibold uppercase">Sense {i + 1}</legend>
          <div className="grid grid-cols-2 gap-3">
            <Field label={`Part of speech (sense ${i + 1})`}>
              <Input value={sense.pos} onChange={(e) => changeSense(i, { pos: e.target.value })} />
            </Field>
            <Field label={`CEFR (sense ${i + 1})`}>
              <Input value={sense.cefr} onChange={(e) => changeSense(i, { cefr: e.target.value as Sense["cefr"] })} />
            </Field>
          </div>
          <Field label={`Explanation (sense ${i + 1})`}>
            <Textarea value={sense.simpleDefinition} onChange={(e) => changeSense(i, { simpleDefinition: e.target.value })} />
          </Field>
          {LEVELS.map((level) => (
            <Field key={level} label={`Example ${level} (sense ${i + 1})`}>
              <Textarea
                value={sense.examples.find((e) => e.level === level)?.text ?? ""}
                onChange={(e) => changeExample(i, level, e.target.value)}
              />
            </Field>
          ))}
          {sense.usage.map((line, k) => (
            <div key={k} className="grid grid-cols-[110px_1fr_1fr] gap-2">
              <Field label={`Usage ${k + 1} kind`}>
                <Input value={line.kind} onChange={(e) => changeUsage(i, k, { kind: e.target.value })} />
              </Field>
              <Field label={`Usage ${k + 1} pattern`}>
                <Input value={line.pattern} onChange={(e) => changeUsage(i, k, { pattern: e.target.value })} />
              </Field>
              <Field label={`Usage ${k + 1} example`}>
                <Input value={line.example} onChange={(e) => changeUsage(i, k, { example: e.target.value })} />
              </Field>
            </div>
          ))}
          <Field label={`Vietnamese (sense ${i + 1})`}>
            <Textarea value={sense.vietnamese} onChange={(e) => changeSense(i, { vietnamese: e.target.value })} />
          </Field>
        </fieldset>
      ))}
    </div>
  );
}

export function WordsPage() {
  return (
    <EntryBrowser<WordItem, WordEntry>
      resource="words"
      chips={chips}
      itemTitle={(word) => word.word}
      itemSubtitle={(word) => word.entry.senses[0]?.simpleDefinition ?? ""}
      itemBadges={(word) => <Badge variant="outline">{word.source === "bundled" ? "Bundled" : "Backend"}</Badge>}
      detailRows={detailRows}
      editableEntry={(word) => word.entry}
      renderForm={(draft, setDraft, word) => <WordForm draft={draft} setDraft={setDraft} word={word} />}
    />
  );
}
