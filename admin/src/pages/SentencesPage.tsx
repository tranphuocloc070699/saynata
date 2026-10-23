import type { SentenceExplanation, SentenceItem, SentenceWord } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Input, Textarea } from "@/components/ui/input";
import { Field, FormSection } from "@/components/EntryEditor";
import { EntryBrowser, type Chip, type Column } from "@/components/EntryBrowser";
import type { FieldRow } from "@/components/FieldTable";

const chips: Chip[] = [
  { id: "needs-review", label: "Needs review", description: "Marked for a check.", field: "needs_review", value: true, group: "Status" },
];

// Same order as the app: the sentence, the simple explanation, the words, the translation last.
function detailRows(sentence: SentenceItem): FieldRow[] {
  const { explanation } = sentence;
  return [
    { label: "Sentence", value: sentence.sourceText },
    { label: "Status", value: sentence.needsReview ? <Badge variant="warning">Needs review</Badge> : "Reviewed" },
    { label: "Simple explanation", value: explanation.meaning },
    {
      label: "Words",
      value: (
        <ul className="space-y-1">
          {explanation.words.map((w, i) => (
            <li key={i}>
              <strong>{w.word}</strong> <span className="text-xs text-muted-foreground">{w.partOfSpeech}</span>
              <div className="text-muted-foreground">{w.meaning}</div>
            </li>
          ))}
        </ul>
      ),
    },
    { label: `Translation (${sentence.targetLang})`, value: explanation.translation },
    { label: "Model", value: `${sentence.model} (prompt ${sentence.promptVersion})` },
    { label: "Created", value: sentence.createdAt },
  ];
}

function SentenceForm({
  draft,
  setDraft,
}: {
  draft: SentenceExplanation;
  setDraft: (next: SentenceExplanation) => void;
}) {
  const changeWord = (index: number, patch: Partial<SentenceWord>) =>
    setDraft({ ...draft, words: draft.words.map((w, i) => (i === index ? { ...w, ...patch } : w)) });

  return (
    <div className="space-y-6">
      <FormSection title="Meaning" hint="The simple English explanation the app shows first.">
        <Field label="Simple explanation">
          <Textarea value={draft.meaning} onChange={(e) => setDraft({ ...draft, meaning: e.target.value })} />
        </Field>
      </FormSection>
      <FormSection title="Words" hint="The words of the sentence, as word cards.">
        {draft.words.map((word, i) => (
          <div key={i} className="grid grid-cols-[1fr_120px_2fr] gap-2">
            <Field label={`Word ${i + 1}`}>
              <Input value={word.word} onChange={(e) => changeWord(i, { word: e.target.value })} />
            </Field>
            <Field label={`Part of speech ${i + 1}`}>
              <Input value={word.partOfSpeech} onChange={(e) => changeWord(i, { partOfSpeech: e.target.value })} />
            </Field>
            <Field label={`Meaning ${i + 1}`}>
              <Input value={word.meaning} onChange={(e) => changeWord(i, { meaning: e.target.value })} />
            </Field>
          </div>
        ))}
      </FormSection>
      <FormSection title="Translation" hint="The target language, shown last and closed.">
        <Field label="Translation">
          <Textarea value={draft.translation} onChange={(e) => setDraft({ ...draft, translation: e.target.value })} />
        </Field>
      </FormSection>
    </div>
  );
}

// The table columns after the sentence (same pattern as the Words table, TASK-99.3).
const columns: Column<SentenceItem>[] = [
  { header: "Language", cell: (sentence) => <span className="text-muted-foreground">{sentence.targetLang}</span> },
  {
    header: "Status",
    cell: (sentence) =>
      sentence.needsReview ? (
        <Badge variant="warning" dot>
          Needs review
        </Badge>
      ) : null,
  },
  { header: "Model", cell: (sentence) => <span className="text-muted-foreground">{sentence.model}</span> },
];

export function SentencesPage() {
  return (
    <EntryBrowser<SentenceItem, SentenceExplanation>
      resource="sentences"
      chips={chips}
      itemTitle={(sentence) => sentence.sourceText}
      itemSubtitle={(sentence) => sentence.explanation.meaning}
      columns={columns}
      detailRows={detailRows}
      editableEntry={(sentence) => sentence.explanation}
      renderForm={(draft, setDraft) => <SentenceForm draft={draft} setDraft={setDraft} />}
    />
  );
}
