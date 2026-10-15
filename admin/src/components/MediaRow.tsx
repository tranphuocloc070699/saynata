import { useApiUrl, useInvalidate } from "@refinedev/core";
import { Check, ImageOff, Images, Search, Sparkles, X } from "lucide-react";
import { useState } from "react";
import type { MediaSearchResponse, MediaSearchResult, SenseMedia, WordItem } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { httpClient } from "@/providers/dataProvider";
import { cn } from "@/lib/utils";

// The backend wants "Content-Type: application/json" on every write, even without a body.
const JSON_HEADERS = { "Content-Type": "application/json" };

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
  const senseLabel = `${word.word}, sense ${index + 1}`;
  const mediaUrl = `${apiUrl}/words/${word.id}/senses/${index}/media`;

  const [panelOpen, setPanelOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<MediaSearchResult[]>();
  const [chosen, setChosen] = useState<number>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  async function run(action: () => Promise<unknown>, afterwards?: () => void) {
    setBusy(true);
    setError(undefined);
    try {
      await action();
      afterwards?.();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  // The word list is read again, so the new picture shows at once.
  const refresh = () => invalidate({ resource: "words", invalidates: ["list", "detail"] });

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

  const markForAi = () =>
    run(async () => {
      await httpClient.post(`${mediaUrl}/mark-for-ai`, {}, { headers: JSON_HEADERS });
      await refresh();
    });

  return (
    <div className="space-y-3">
      <div className="flex items-start justify-between gap-3">
        <MediaCell media={media} alt={senseLabel} />
        <div className="flex shrink-0 gap-2" role="group" aria-label={`Media actions (sense ${index + 1})`}>
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
            aria-label="Mark for AI image"
            title="Mark for AI image"
            aria-pressed={markedForAi}
            disabled={busy || markedForAi}
            onClick={markForAi}
            className={cn(markedForAi && "border-primary text-primary")}
          >
            <Sparkles className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {markedForAi && (
        <p className="flex items-center gap-1 text-xs text-primary">
          <Sparkles className="h-3 w-3" aria-hidden /> Waiting for an AI image
        </p>
      )}

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
                <li key={`${result.kind}-${result.pixabayId}`}>
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
    </div>
  );
}

// The photo (or the muted looping clip) of a sense, with the Pixabay credit under it.
function MediaCell({ media, alt }: { media: SenseMedia | null | undefined; alt: string }) {
  if (!media) return <span>-</span>;
  return (
    <div className="space-y-1">
      {media.kind === "clip" ? (
        <video src={media.url} aria-label={alt} className="h-24 rounded-md" autoPlay loop muted playsInline />
      ) : (
        <img src={media.url} alt={alt} className="h-24 rounded-md" />
      )}
      <div className="text-xs text-muted-foreground">
        {media.kind === "clip" ? "Clip" : "Photo"} by{" "}
        <a href={media.pageUrl} target="_blank" rel="noreferrer" className="underline">
          {media.photographer}
        </a>{" "}
        on Pixabay
      </div>
    </div>
  );
}

// The backend answers errors as a ProblemDetail: the readable text is in "detail".
function errorText(error: unknown): string {
  const response = (error as { response?: { data?: { detail?: string } } }).response;
  return response?.data?.detail ?? (error as Error).message ?? "Something went wrong.";
}
