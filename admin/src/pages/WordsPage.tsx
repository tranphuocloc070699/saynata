import { Play } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";
import type { Example, ExampleLevel, Sense, SenseMedia, UsageLine, WordEntry, WordItem } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { cn } from "@/lib/utils";
import { Input, Textarea } from "@/components/ui/input";
import { Field, FormSection } from "@/components/EntryEditor";
import { MediaRow } from "@/components/MediaRow";
import { EntryBrowser, type Chip, type Column } from "@/components/EntryBrowser";
import type { FieldRow } from "@/components/FieldTable";

const LEVELS: ExampleLevel[] = ["easy", "medium", "harder"];

// Every choice of the Filter menu is sent to the backend (a query parameter), except Pending (TASK-101).
// Level: OR-ed (the level of the first sense, the backend column). Source: one at a time.
const cefrChip = (label: string, levels: string[]): Chip => ({
  id: label,
  label,
  field: "cefr",
  value: levels,
  combine: "any",
  group: "Level",
});

const chips: Chip[] = [
  cefrChip("A1-A2", ["A1", "A2"]),
  cefrChip("B1-B2", ["B1", "B2"]),
  cefrChip("C1-C2", ["C1", "C2"]),
  { id: "bundled", label: "Bundled", description: "On the phone, inside the app. Works offline.", field: "source", value: "bundled", group: "Source" },
  {
    id: "backend",
    label: "Backend database",
    description: "Written by Claude on the server when someone looked it up.",
    field: "source",
    value: "backend",
    group: "Source",
  },
  {
    id: "pending",
    label: "Pending",
    description: "Met in a sentence, not in the database yet. The night job answers it.",
    field: "",
    value: "",
    group: "Status",
    pending: true,
  },
  { id: "needs-review", label: "Needs review", description: "The text check found a problem.", field: "needs_review", value: true, group: "Status" },
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

// The one status badge of a row (TASK-101): "Needs review · N" with N problems, the reasons in the tooltip.
// A word that is fine shows nothing.
function NeedsReviewBadge({ word }: { word: WordItem }) {
  if (!word.needsReview) return null;
  const reasons = word.reviewReasons ?? [];
  const tooltip = reasons.length ? reasons.join("\n") : "No problems found now; press ✓ to mark it reviewed";
  return (
    <Badge variant="warning" dot title={tooltip}>
      Needs review · {reasons.length}
    </Badge>
  );
}

// A clip plays in the thumbnail while the mouse is over the row. The <video> exists only then (the list holds
// at most one), so 50 rows never load 50 clips. Leaving unmounts it: pause, back to the start, the still shows again.
function HoverClip({ url }: { url: string }) {
  const video = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const element = video.current;
    // play() can fail (no codec, the page is hidden): the still stays, nothing else to do.
    try {
      void element?.play()?.catch(() => undefined);
    } catch {
      // not supported here (for example a test browser)
    }
    return () => {
      element?.pause();
      if (element) element.currentTime = 0;
    };
  }, []);
  return (
    <video
      ref={video}
      src={url}
      preload="auto"
      muted
      loop
      playsInline
      aria-hidden
      className="absolute inset-0 h-full w-full bg-muted object-cover"
    />
  );
}

const SOURCE_NAMES = { pixabay: "Pixabay", ai: "AI", upload: "Upload" } as const;

// The caption of the big preview: word, sense number, where the file came from, and the review state when it matters.
function previewCaption(word: WordItem, media: SenseMedia): string {
  const parts = [word.word, "sense 1", SOURCE_NAMES[media.source ?? "pixabay"]];
  const state = word.mediaReview?.[0]?.state;
  if (state === "wrong") parts.push("Wrong picture");
  else if (state === "to_review") parts.push("Needs review");
  return parts.join(" \u00b7 ");
}

// The big picture that opens ~300 ms after the mouse rests on a thumbnail (list rows and grid cards, TASK-81).
// A clip plays here, muted and looped. The card is only in the page while it is open, so one clip loads at most.
// It slides and flips to stay inside the window. A finger never opens it.
function PreviewCard({ word, media, children }: { word: WordItem; media: SenseMedia; children: ReactNode }) {
  return (
    <HoverCard openDelay={300} closeDelay={100}>
      <HoverCardTrigger asChild>{children}</HoverCardTrigger>
      <HoverCardContent side="right" className="w-auto max-w-[min(340px,90vw)] space-y-2">
        {media.kind === "clip" ? (
          <video
            src={media.url}
            aria-label={`${word.word} clip preview`}
            autoPlay
            loop
            muted
            playsInline
            className="max-h-[300px] w-full rounded-md bg-muted object-contain"
          />
        ) : (
          <img src={media.url} alt={`${word.word} preview`} className="max-h-[300px] w-full rounded-md bg-muted object-contain" />
        )}
        <p className="text-xs text-muted-foreground">{previewCaption(word, media)}</p>
      </HoverCardContent>
    </HoverCard>
  );
}

// The picture of a list row (about 56x42) or a grid card (4:3, full card width): the photo of the first sense, a play
// icon for a clip, or a quiet dash when the word has no media. Photos load lazily, so a long list stays light.
// In the list, a clip also plays inside the thumbnail while the mouse is over the row.
function Thumb({ word, hovered, variant }: { word: WordItem; hovered: boolean; variant: "row" | "card" }) {
  const media = word.media?.[0];
  const card = variant === "card";
  const box = cn(
    "relative flex shrink-0 items-center justify-center overflow-hidden rounded-md bg-muted text-muted-foreground",
    card ? "aspect-[4/3] w-full" : "h-[42px] w-14",
  );
  if (media?.kind === "photo") {
    return (
      <PreviewCard word={word} media={media}>
        <span className={box}>
          <img src={media.url} alt={`${word.word} photo`} loading="lazy" className="h-full w-full object-cover" />
        </span>
      </PreviewCard>
    );
  }
  if (media?.kind === "clip") {
    return (
      <PreviewCard word={word} media={media}>
        <span className={box} role="img" aria-label={`${word.word} clip`}>
          {/* In a card the first frame is the picture; the play icon sits on top of it. */}
          {card && <video src={media.url} preload="metadata" muted playsInline aria-hidden className="h-full w-full object-cover" />}
          <Play className={card ? "absolute h-6 w-6 rounded-full bg-black/60 p-1 text-white" : "h-4 w-4"} aria-hidden />
          {!card && hovered && <HoverClip url={media.url} />}
        </span>
      </PreviewCard>
    );
  }
  return (
    <span className={box} aria-label="No media">
      -
    </span>
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
      // The key resets the row (open panel, results, error) when another word or sense is shown (TASK-64).
      value: <MediaRow key={`${word.id}:${index}`} word={word} index={index} />,
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
        <div className="space-y-2 rounded-lg bg-warning-soft p-4 text-warning">
          <Badge variant="warning" dot>
            Needs review
          </Badge>
          <ReviewReasons word={word} />
        </div>
      )}
      <p className="text-xs text-muted-foreground">IPA is shown in the table but is not changed here (the backend saves only the entry).</p>
      {draft.senses.map((sense, i) => (
        <FormSection key={i} title={`Sense ${i + 1}`} hint="Simple English first. Vietnamese is the last field.">
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
        </FormSection>
      ))}
    </div>
  );
}

// The table columns after the word (Isomorphic list page, TASK-99.2). Only problems get a badge (TASK-101).
const columns: Column<WordItem>[] = [
  { header: "Level", cell: (word) => word.entry.senses[0]?.cefr ?? "-" },
  { header: "Source", cell: (word) => <span className="text-muted-foreground">{word.source === "bundled" ? "Bundled" : "Backend"}</span> },
  { header: "Status", cell: (word) => <NeedsReviewBadge word={word} /> },
];

export function WordsPage() {
  return (
    <EntryBrowser<WordItem, WordEntry>
      resource="words"
      chips={chips}
      itemTitle={(word) => word.word}
      itemSubtitle={(word) => word.entry.senses[0]?.vietnamese ?? ""}
      itemThumb={(word, hovered, variant) => <Thumb word={word} hovered={hovered} variant={variant} />}
      columns={columns}
      hasMedia={(word) => !!word.media?.some(Boolean)}
      itemBadges={(word) => <NeedsReviewBadge word={word} />}
      detailRows={detailRows}
      editableEntry={(word) => word.entry}
      renderForm={(draft, setDraft, word) => <WordForm draft={draft} setDraft={setDraft} word={word} />}
    />
  );
}
