import { useApiUrl, useInvalidate } from "@refinedev/core";
import { Check, ImageOff, Images, Loader2, Maximize2, Search, Sparkles, Upload, X } from "lucide-react";
import { useEffect, useRef, useState, type ChangeEvent, type DragEvent } from "react";
import type { AiJob, MediaSearchResponse, MediaSearchResult, SenseMedia, WordItem } from "@/api/types";
import { AiPictureDialog } from "@/components/AiPictureDialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { MediaLightbox, type LightboxMedia } from "@/components/MediaLightbox";
import { httpClient } from "@/providers/dataProvider";
import { cn } from "@/lib/utils";

// The backend wants "Content-Type: application/json" on every write, even without a body.
const JSON_HEADERS = { "Content-Type": "application/json" };

// How often a drawing AI picture asks the server if it is done (TASK-91).
export const AI_POLL_MS = 3000;

interface Props {
  word: WordItem;
  // Position of the sense, 0 = first sense.
  index: number;
}

// The Media row of one sense: the current photo or clip with its credit, three icon buttons, and (when "Pick another"
// is on) an inline panel under it with up to 6 Pixabay results (TASK-52, mockup option A).
export function MediaRow({ word, index }: Props) {
  const apiUrl = useApiUrl();
  const invalidate = useInvalidate();
  const media = word.media?.[index];
  const markedForAi = word.mediaStatus?.[index] === "needs_ai";
  const review = word.mediaReview?.[index];
  const approved = review?.state === "approved";
  const senseLabel = `${word.word}, sense ${index + 1}`;
  const mediaUrl = `${apiUrl}/words/${word.id}/senses/${index}/media`;

  const [panelOpen, setPanelOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<MediaSearchResult[]>();
  const [chosen, setChosen] = useState<number>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  // The full-size viewer: the photo or clip that was clicked (the current one or a search result).
  const [viewing, setViewing] = useState<LightboxMedia | null>(null);
  // A file is being uploaded, or is being dragged over the row (TASK-79).
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  async function run(action: () => Promise<unknown>, afterwards?: () => void, toText: (e: unknown) => string = errorText) {
    setBusy(true);
    setError(undefined);
    try {
      await action();
      afterwards?.();
    } catch (e) {
      setError(toText(e));
    } finally {
      setBusy(false);
    }
  }

  // The word list is read again, so the new picture shows at once. "all" is needed: the list is an infinite list,
  // and Refine's "list" key does not reach it (TASK-57).
  const refresh = () => invalidate({ resource: "words", invalidates: ["all"] });

  // words is the search text; empty means "use the sense's own search words" (the backend answers with them).
  const search = (words: string) =>
    run(async () => {
      const { data } = await httpClient.get<MediaSearchResponse>(`${apiUrl}/media/search`, {
        params: { sense: `${word.id}:${index}`, ...(words.trim() ? { q: words.trim() } : {}) },
      });
      setQuery(data.query);
      setResults(data.results);
      setChosen(undefined);
    });

  function openPanel() {
    setPanelOpen(true);
    void search("");
  }

  const apply = () =>
    run(
      async () => {
        const { previewUrl: _preview, ...body } = results![chosen!];
        await httpClient.put(mediaUrl, body, { headers: JSON_HEADERS });
        await refresh();
      },
      () => setPanelOpen(false),
    );

  const noImage = () =>
    run(async () => {
      await httpClient.delete(mediaUrl, { data: {}, headers: JSON_HEADERS });
      await refresh();
    }, () => setPanelOpen(false));

  // The AI picture (TASK-91): the Sparkles button opens a dialog; OK starts a job on the server. The job state is in
  // the database, so a reloaded page finds it again: the word says mediaStatus "ai_drawing" and this row keeps asking.
  const [dialogOpen, setDialogOpen] = useState(false);
  const [job, setJob] = useState<AiJob>();
  const drawing = job ? job.status === "queued" || job.status === "drawing" : word.mediaStatus?.[index] === "ai_drawing";
  const jobError = job?.status === "failed" ? (job.error ?? "The AI picture could not be drawn.") : undefined;

  useEffect(() => {
    if (!drawing) return;
    const timer = setInterval(() => {
      httpClient
        .get<AiJob>(`${mediaUrl}/ai-picture`, { validateStatus: (status) => status === 200 || status === 204 })
        .then(({ data, status }) => {
          if (status === 204 || !data) return;
          setJob(data);
          // A finished job: read the word again, so the new picture (or the old one) shows.
          if (data.status === "done" || data.status === "failed") void refresh();
        })
        .catch(() => undefined); // a short network problem: ask again in 3 seconds
    }, AI_POLL_MS);
    return () => clearInterval(timer);
    // refresh is made again on every render but does the same thing
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [drawing, mediaUrl]);

  // "Picture looks right" (TASK-80): approve the current file, or undo it. Approving also clears "needs AI image".
  const toggleApproved = () =>
    run(async () => {
      if (approved) await httpClient.delete(`${mediaUrl}/approve`, { data: {}, headers: JSON_HEADERS });
      else await httpClient.post(`${mediaUrl}/approve`, {}, { headers: JSON_HEADERS });
      await refresh();
    });

  // The admin's own picture or clip (TASK-79). The browser sets the multipart Content-Type itself (with the boundary);
  // the backend needs X-Requested-With for a form upload (CSRF rule in JsonWritesOnlyFilter).
  async function uploadFile(file: File) {
    setUploading(true);
    await run(
      async () => {
        const form = new FormData();
        form.append("file", file);
        await httpClient.post(`${mediaUrl}/upload`, form, { headers: { "X-Requested-With": "XMLHttpRequest" } });
        await refresh();
      },
      () => setPanelOpen(false),
      uploadErrorText,
    );
    setUploading(false);
  }

  const working = busy || uploading;

  function onFileChosen(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = ""; // so the same file can be chosen again
    if (file && !working) void uploadFile(file);
  }

  // Drag and drop of a file onto the Media row. Only real files count (not dragged text).
  const hasFiles = (event: DragEvent) => Array.from(event.dataTransfer?.types ?? []).includes("Files");

  function onDragOver(event: DragEvent) {
    if (!hasFiles(event)) return;
    event.preventDefault(); // without this the browser would open the file
    setDragging(true);
  }

  function onDragLeave(event: DragEvent) {
    // Moving over a child element also fires dragleave on the row: ignore that.
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragging(false);
  }

  function onDrop(event: DragEvent) {
    if (!hasFiles(event)) return;
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files?.[0];
    if (file && !working) void uploadFile(file);
  }

  return (
    <div
      className={cn("space-y-3 rounded-md", dragging && "outline-dashed outline-2 outline-offset-4 outline-primary/60")}
      onDragEnter={onDragOver}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      <div className="flex items-start justify-between gap-3">
        {drawing ? (
          <div
            role="status"
            className="flex h-24 w-36 flex-col items-center justify-center gap-1 rounded-md border border-dashed border-primary/60 bg-muted/40 p-2 text-center text-xs text-muted-foreground"
          >
            <Loader2 className="h-5 w-5 animate-spin text-primary" aria-hidden />
            Drawing an AI picture&hellip; you can leave this page
          </div>
        ) : (
          <MediaCell media={media} alt={senseLabel} onOpen={() => media && setViewing(media)} />
        )}
        <div className="flex shrink-0 items-center gap-2" role="group" aria-label={`Media actions (sense ${index + 1})`}>
          {approved && <span className="text-xs font-medium text-primary">Approved</span>}
          {media && (
            <Button
              variant="outline"
              size="icon"
              aria-label="Picture looks right"
              title={approved ? "Approved: press to undo" : "Picture looks right"}
              aria-pressed={approved}
              disabled={busy}
              onClick={toggleApproved}
              className={cn(approved && "border-primary text-primary")}
            >
              <Check className="h-4 w-4" />
            </Button>
          )}
          <Button
            variant="outline"
            size="icon"
            aria-label="Pick another image"
            title="Pick another image"
            aria-expanded={panelOpen}
            disabled={busy}
            onClick={openPanel}
          >
            <Images className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" aria-label="No image" title="No image" disabled={busy} onClick={noImage}>
            <ImageOff className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Draw an AI picture"
            title="Draw an AI picture"
            aria-haspopup="dialog"
            disabled={busy || drawing}
            onClick={() => setDialogOpen(true)}
            className={cn(markedForAi && "border-primary text-primary")}
          >
            <Sparkles className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Upload a picture or clip"
            title="Upload a picture or clip"
            disabled={working}
            onClick={() => fileInput.current?.click()}
          >
            {uploading ? (
              <Loader2 role="status" aria-label="Uploading" className="h-4 w-4 animate-spin" />
            ) : (
              <Upload className="h-4 w-4" />
            )}
          </Button>
          {/* The file picker. The Upload button above opens it. */}
          <input
            ref={fileInput}
            type="file"
            accept="image/*,video/*"
            aria-label="Choose a picture or clip"
            tabIndex={-1}
            className="hidden"
            onChange={onFileChosen}
          />
        </div>
      </div>

      {media && review?.verdict && (
        <p className="text-xs text-muted-foreground">
          Claude: {review.verdict}
          {review.reason ? ` \u2014 ${review.reason}` : ""}
        </p>
      )}

      {markedForAi && !drawing && (
        <p className="flex items-center gap-1 text-xs text-primary">
          <Sparkles className="h-3 w-3" aria-hidden /> Waiting for an AI image
        </p>
      )}

      {jobError && (
        <p role="alert" className="text-sm text-destructive">
          The AI picture failed: {jobError} The old picture is kept.
        </p>
      )}

      <AiPictureDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        mediaUrl={mediaUrl}
        word={word.word}
        senseNumber={index + 1}
        pos={word.entry?.senses?.[index]?.pos}
        meaning={word.entry?.senses?.[index]?.simpleDefinition}
        media={media}
        onStarted={(started) => {
          setJob(started);
          void refresh(); // the word now says ai_drawing
        }}
      />

      {panelOpen && (
        <section aria-label={`Pick another image (sense ${index + 1})`} className="space-y-3 rounded-md border border-border bg-muted/40 p-3">
          <form
            className="flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              void search(query);
            }}
          >
            <Input aria-label="Search words" value={query} onChange={(e) => setQuery(e.target.value)} />
            <Button type="submit" variant="outline" size="icon" aria-label="Search" title="Search" disabled={busy}>
              <Search className="h-4 w-4" />
            </Button>
            <Button variant="outline" size="icon" aria-label="Close search" title="Close search" onClick={() => setPanelOpen(false)}>
              <X className="h-4 w-4" />
            </Button>
          </form>

          {results && results.length === 0 && <p className="text-sm text-muted-foreground">Pixabay has nothing for these words.</p>}

          {results && results.length > 0 && (
            <ul className="grid grid-cols-3 gap-2">
              {results.map((result, i) => (
                <li key={`${result.kind}-${result.pixabayId}`} className="relative">
                  <button
                    type="button"
                    aria-label={`Result ${i + 1}, ${result.kind} by ${result.photographer}`}
                    aria-pressed={chosen === i}
                    onClick={() => setChosen(i)}
                    className={cn(
                      "relative block w-full overflow-hidden rounded-md border-2 bg-card",
                      chosen === i ? "border-primary" : "border-transparent hover:border-border",
                    )}
                  >
                    <img src={result.previewUrl} alt="" className="aspect-[4/3] w-full object-cover" />
                    {result.kind === "clip" && (
                      <span className="absolute left-1 top-1 rounded bg-black/70 px-1.5 py-0.5 text-[10px] font-medium text-white">clip</span>
                    )}
                  </button>
                  {/* A separate button, so a click on the tile still selects it. */}
                  <button
                    type="button"
                    aria-label={`View result ${i + 1} full size`}
                    title="View full size"
                    onClick={() =>
                      setViewing({ url: result.downloadUrl, kind: result.kind, photographer: result.photographer, pageUrl: result.pageUrl })
                    }
                    className="absolute right-1 top-1 inline-flex h-6 w-6 items-center justify-center rounded bg-black/70 text-white hover:bg-black/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <Maximize2 className="h-3 w-3" />
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="flex justify-end">
            <Button size="icon" aria-label="Use this image" title="Use this image" disabled={busy || chosen === undefined} onClick={apply}>
              <Check className="h-4 w-4" />
            </Button>
          </div>
        </section>
      )}

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      <MediaLightbox media={viewing} label={`${word.word} · sense ${index + 1}`} onClose={() => setViewing(null)} />
    </div>
  );
}

// The photo (or the muted looping clip) of a sense, with the Pixabay credit under it.
function MediaCell({ media, alt, onOpen }: { media: SenseMedia | null | undefined; alt: string; onOpen: () => void }) {
  if (!media) return <span>-</span>;
  return (
    <div className="space-y-1">
      <button
        type="button"
        aria-label={`View ${alt} full size`}
        title="View full size"
        onClick={onOpen}
        className="block cursor-zoom-in rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
      {media.kind === "clip" ? (
        <video key={media.url} src={media.url} aria-label={alt} className="h-24 rounded-md" autoPlay loop muted playsInline />
      ) : (
        <img src={media.url} alt={alt} className="h-24 rounded-md" />
      )}
      </button>
      <div className="text-xs text-muted-foreground">
        {media.source === "upload" ? (
          "Uploaded by the admin"
        ) : media.source === "ai" ? (
          media.licenseNote ?? "AI image"
        ) : (
          <>
            {media.kind === "clip" ? "Clip" : "Photo"} by{" "}
            <a href={media.pageUrl ?? undefined} target="_blank" rel="noreferrer" className="underline">
              {media.photographer}
            </a>{" "}
            on Pixabay
          </>
        )}
      </div>
    </div>
  );
}

// The backend answers errors as a ProblemDetail: the readable text is in "detail".
function errorText(error: unknown): string {
  const response = (error as { response?: { data?: { detail?: string } } }).response;
  return response?.data?.detail ?? (error as Error).message ?? "Something went wrong.";
}

// A file that is too big can be refused by the web server before the backend answers, with no JSON detail (TASK-79).
function uploadErrorText(error: unknown): string {
  const response = (error as { response?: { status?: number; data?: { detail?: string } } }).response;
  if (!response?.data?.detail && response?.status === 413) return "That file is too big.";
  return errorText(error);
}
