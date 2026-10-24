import { useApiUrl } from "@refinedev/core";
import { FileUp, Loader2 } from "lucide-react";
import { useRef, useState, type DragEvent } from "react";
import type { SourceCandidate, SourceQueueResult, SourceView } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { httpClient } from "@/providers/dataProvider";

type Filter = "useful" | "rare" | "names";

const ROWS_PER_STEP = 200;

const keyOf = (c: SourceCandidate) => `${c.kind}:${c.text}`;

// The backend answers errors as a ProblemDetail: the readable text is in "detail".
function errorText(error: unknown): string {
  const response = (error as { response?: { status?: number; data?: { detail?: string } } }).response;
  if (!response?.data?.detail && response?.status === 413) return "That file is too big. The limit is 5 MB.";
  return response?.data?.detail ?? (error as Error).message ?? "Something went wrong.";
}

// Sources (TASK-108): upload a book (.txt or .md) with a title, see the words and phrases that are not in the
// database yet, pick some and add them to the prefill queue. The text of the book is never kept, only the word list.
export function SourcesPage() {
  const apiUrl = useApiUrl();
  const fileInput = useRef<HTMLInputElement>(null);
  const [title, setTitle] = useState("");
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [queueing, setQueueing] = useState(false);
  const [error, setError] = useState<string>();
  const [view, setView] = useState<SourceView>();
  const [result, setResult] = useState<SourceQueueResult>();
  const [filter, setFilter] = useState<Filter>("useful");
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [shown, setShown] = useState(ROWS_PER_STEP);

  async function upload(file: File) {
    setUploading(true);
    setError(undefined);
    setResult(undefined);
    const form = new FormData();
    form.append("file", file);
    // The backend uses the file name when the title is empty, so show that name in the field.
    const name = title.trim() || file.name.replace(/\.[^.]*$/, "");
    form.append("title", name);
    try {
      // No Content-Type here: the browser adds multipart/form-data with its boundary.
      const { data } = await httpClient.post<SourceView>(`${apiUrl}/sources`, form, {
        headers: { "X-Requested-With": "XMLHttpRequest" },
      });
      setTitle(data.title);
      setView(data);
      setPicked(new Set());
      setFilter("useful");
      setShown(ROWS_PER_STEP);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setUploading(false);
    }
  }

  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files?.[0];
    if (file && !uploading) void upload(file);
  }

  async function addToQueue() {
    if (!view || picked.size === 0) return;
    setQueueing(true);
    setError(undefined);
    const items = view.candidates.filter((c) => picked.has(keyOf(c))).map((c) => ({ kind: c.kind, text: c.text }));
    try {
      const { data } = await httpClient.post<SourceQueueResult>(
        `${apiUrl}/sources/${view.id}/queue`,
        { items },
        { headers: { "Content-Type": "application/json" } },
      );
      setResult(data);
      // The snippets are gone on the server; the list stays for reading.
      setView({ ...view, status: "queued", queuedCount: data.queued, candidates: view.candidates.map((c) => ({ ...c, snippet: "" })) });
      setPicked(new Set());
    } catch (e) {
      setError(errorText(e));
    } finally {
      setQueueing(false);
    }
  }

  function reset() {
    setView(undefined);
    setResult(undefined);
    setError(undefined);
    setPicked(new Set());
    setTitle("");
  }

  const queued = view?.status === "queued";
  const rows = (view?.candidates ?? []).filter((c) =>
    filter === "names" ? c.kind === "name" : c.kind !== "name" && (filter === "useful" ? c.useful : !c.useful),
  );
  const visible = rows.slice(0, shown);

  function toggle(c: SourceCandidate) {
    setPicked((current) => {
      const next = new Set(current);
      if (!next.delete(keyOf(c))) next.add(keyOf(c));
      return next;
    });
  }

  function toggleAll() {
    const keys = rows.map(keyOf);
    setPicked((current) => {
      const next = new Set(current);
      if (keys.every((k) => next.has(k))) keys.forEach((k) => next.delete(k));
      else keys.forEach((k) => next.add(k));
      return next;
    });
  }

  const counts = view?.counts;
  const tiles = [
    { label: "Words in file", value: counts?.wordsInFile },
    { label: "Known", value: counts?.known },
    { label: "New words", value: counts?.newWords },
    { label: "New phrases", value: counts?.newPhrases },
  ];
  const chips: { id: Filter; label: string; count?: number }[] = [
    { id: "useful", label: "Useful", count: counts?.useful },
    { id: "rare", label: "Rare", count: counts?.rare },
    { id: "names", label: "Names skipped", count: counts?.namesSkipped },
  ];

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="grid gap-4 border-b border-border p-4 md:grid-cols-[1fr_280px]">
          <div
            role="group"
            aria-label="Upload a book"
            onDragOver={(e) => {
              e.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
            className={cn(
              "flex flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-border px-4 py-8 text-center",
              dragging && "border-primary bg-accent",
            )}
          >
            {uploading ? (
              <Loader2 role="status" aria-label="Reading the file" className="h-6 w-6 animate-spin text-muted-foreground" />
            ) : (
              <FileUp aria-hidden className="h-6 w-6 text-muted-foreground" />
            )}
            <p className="font-medium">Drop a .txt or .md file</p>
            <p className="text-sm text-muted-foreground">Up to 5 MB. Only the word list is kept, never the text.</p>
            <input
              ref={fileInput}
              type="file"
              accept=".txt,.md,.markdown,text/plain,text/markdown"
              aria-label="Choose a .txt or .md file"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = ""; // so the same file can be chosen again
                if (file) void upload(file);
              }}
            />
            <Button variant="outline" className="mt-2" disabled={uploading} onClick={() => fileInput.current?.click()}>
              Choose file
            </Button>
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="source-title" className="text-sm font-medium">
              Title
            </label>
            <Input
              id="source-title"
              value={title}
              maxLength={100}
              placeholder="For example: Alice in Wonderland"
              onChange={(e) => setTitle(e.target.value)}
            />
            <span className="text-xs text-muted-foreground">
              Queued words get the source "book:{title.trim() || "title"}". Empty: the file name.
            </span>
          </div>
        </div>

        {error && (
          <p role="alert" className="border-b border-border px-4 py-2 text-sm text-destructive">
            {error}
          </p>
        )}
        {result && (
          <p role="status" className="border-b border-border bg-success-soft px-4 py-2 text-sm text-success">
            Added {result.queued} to the queue
            {result.alreadyQueued > 0 && ` (${result.alreadyQueued} were already there)`}. The night job will write them.{" "}
            <button type="button" className="font-medium underline" onClick={reset}>
              Upload another file
            </button>
          </p>
        )}

        {view && counts && (
          <>
            <div className="grid grid-cols-2 gap-3 p-4 md:grid-cols-4">
              {tiles.map((tile) => (
                <div key={tile.label} className="rounded-xl border border-border p-3">
                  <p className="text-xs text-muted-foreground">{tile.label}</p>
                  <p className="font-heading text-2xl font-semibold tabular-nums">{(tile.value ?? 0).toLocaleString("en-US")}</p>
                </div>
              ))}
            </div>

            <div className="flex flex-wrap gap-2 px-4 pb-3" role="group" aria-label="Filter">
              {chips.map((chip) => (
                <button
                  key={chip.id}
                  type="button"
                  aria-pressed={filter === chip.id}
                  onClick={() => {
                    setFilter(chip.id);
                    setShown(ROWS_PER_STEP);
                  }}
                  className={cn(
                    "rounded-full border border-border px-3 py-1 text-sm font-medium hover:bg-muted",
                    filter === chip.id && "border-transparent bg-accent text-accent-foreground",
                  )}
                >
                  {chip.label} <span className="tabular-nums text-muted-foreground">{(chip.count ?? 0).toLocaleString("en-US")}</span>
                </button>
              ))}
            </div>

            <table aria-label="Candidates" className="w-full border-collapse text-sm">
              <thead className="sticky top-0 z-10 bg-muted">
                <tr>
                  <th scope="col" className="w-12 border-y border-border px-4 py-3 text-left">
                    {filter !== "names" && !queued && (
                      <input
                        type="checkbox"
                        aria-label="Pick all shown"
                        checked={rows.length > 0 && rows.every((c) => picked.has(keyOf(c)))}
                        onChange={toggleAll}
                      />
                    )}
                  </th>
                  {["Word", "Times", "Common", "Seen in"].map((header) => (
                    <th
                      key={header}
                      scope="col"
                      className="border-y border-border px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-wider text-muted-foreground"
                    >
                      {header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={5} className="p-4 text-muted-foreground">
                      {filter === "names" ? "No names were skipped." : "Nothing here."}
                    </td>
                  </tr>
                )}
                {visible.map((c) => (
                  <tr key={keyOf(c)} className={cn("border-b border-border/60", picked.has(keyOf(c)) && "bg-accent/50")}>
                    <td className="px-4 py-2.5">
                      {c.kind !== "name" && (
                        <input
                          type="checkbox"
                          aria-label={`Pick ${c.text}`}
                          checked={picked.has(keyOf(c))}
                          disabled={queued}
                          onChange={() => toggle(c)}
                        />
                      )}
                    </td>
                    <td className="px-4 py-2.5 font-semibold">
                      {c.text}{" "}
                      {c.kind === "phrase" && <Badge variant="default">phrase</Badge>}
                    </td>
                    <td className="px-4 py-2.5 tabular-nums">{c.times}</td>
                    <td className="px-4 py-2.5 tabular-nums" title="Zipf score: 7 is very common, below 3 is rare">
                      {c.zipf > 0 ? c.zipf.toFixed(1) : "-"}
                    </td>
                    <td className="max-w-md px-4 py-2.5 text-muted-foreground">{c.snippet}</td>
                  </tr>
                ))}
                {rows.length > visible.length && (
                  <tr>
                    <td colSpan={5} className="p-3 text-center">
                      <Button variant="outline" onClick={() => setShown((n) => n + ROWS_PER_STEP)}>
                        Show more ({(rows.length - visible.length).toLocaleString("en-US")} left)
                      </Button>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </>
        )}
      </div>

      {view && (
        <div className="flex items-center justify-between border-t border-border p-3">
          <span className="text-sm text-muted-foreground">{picked.size.toLocaleString("en-US")} picked</span>
          <Button disabled={picked.size === 0 || queueing || queued} onClick={addToQueue}>
            {queueing && <Loader2 aria-hidden className="h-4 w-4 animate-spin" />}
            Add {picked.size} to queue
          </Button>
        </div>
      )}
    </div>
  );
}
