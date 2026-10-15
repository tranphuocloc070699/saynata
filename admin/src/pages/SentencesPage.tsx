import type { SentenceExplanation, SentenceItem, SentenceWord } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Input, Textarea } from "@/components/ui/input";
import { Field } from "@/components/EntryEditor";
import { EntryBrowser, type Chip } from "@/components/EntryBrowser";
import type { FieldRow } from "@/components/FieldTable";

const chips: Chip<SentenceItem>[] = [
  { id: "needs-review", label: "Needs review", server: { field: "needs_review", value: true } },
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
    <div className="space-y-4">
      <Field label="Simple explanation">
        <Textarea value={draft.meaning} onChange={(e) => setDraft({ ...draft, meaning: e.target.value })} />
      </Field>
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
      <Field label="Translation">
        <Textarea value={draft.translation} onChange={(e) => setDraft({ ...draft, translation: e.target.value })} />
      </Field>
    </div>
  );
}

export function SentencesPage() {
  return (
    <EntryBrowser<SentenceItem, SentenceExplanation>
      resource="sentences"
      chips={chips}
      itemTitle={(sentence) => sentence.sourceText}
      itemSubtitle={(sentence) => sentence.explanation.meaning}
      detailRows={detailRows}
      editableEntry={(sentence) => sentence.explanation}
      renderForm={(draft, setDraft) => <SentenceForm draft={draft} setDraft={setDraft} />}
    />
  );
}
