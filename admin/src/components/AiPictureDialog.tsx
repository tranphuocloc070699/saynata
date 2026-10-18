import { Check, Loader2, Sparkles, Undo2, WandSparkles, X } from "lucide-react";
import { useEffect, useState } from "react";
import type { AiJob, AiPrompt, AiPromptSource, AiUsage, SenseMedia } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/input";
import { httpClient } from "@/providers/dataProvider";

// The backend wants "Content-Type: application/json" on every write.
const JSON_HEADERS = { "Content-Type": "application/json" };

// Same limit as the server (AiPictureService.MAX_PROMPT_LENGTH).
export const MAX_PROMPT_LENGTH = 4000;

interface Props {
  open: boolean;
  onClose: () => void;
  // The URL of the sense's media: <api>/words/<id>/senses/<n>/media
  mediaUrl: string;
  // "run", and the sense: "sense 1 (verb)"
  word: string;
  senseNumber: number;
  pos?: string;
  meaning?: string;
  media?: SenseMedia | null;
  // The server started the job (202). The row then shows the drawing state.
  onStarted: (job: AiJob) => void;
}

// The AI picture dialog (TASK-91, layout A): the prompt is filled in (the newest media-ai scene, else a template),
// the admin edits it, and OK starts the background job on the server. Buttons have icons only (CLAUDE.md UI rule).
export function AiPictureDialog({ open, onClose, mediaUrl, word, senseNumber, pos, meaning, media, onStarted }: Props) {
  const [prompt, setPrompt] = useState("");
  // The prompt as the server gave it (or the wand), and where it came from. "Put back the first prompt" restores the
  // very first one of this dialog.
  const [loaded, setLoaded] = useState<AiPrompt>();
  const [first, setFirst] = useState<AiPrompt>();
  const [usage, setUsage] = useState<AiUsage>();
  const [loading, setLoading] = useState(false);
  const [wandBusy, setWandBusy] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fieldError, setFieldError] = useState<string>();
  const [error, setError] = useState<string>();

  // Every time the dialog opens, the prompt is read again (a scene may have been saved since).
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    setError(undefined);
    setFieldError(undefined);
    setLoaded(undefined);
    setFirst(undefined);
    setPrompt("");
    httpClient
      .get<AiPrompt>(`${mediaUrl}/ai-prompt`)
      .then(({ data }) => {
        if (cancelled) return;
        setLoaded(data);
        setFirst(data);
        setUsage(data.usage);
        setPrompt(data.prompt);
      })
      .catch((e) => !cancelled && setError(errorText(e)))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [open, mediaUrl]);

  const busy = loading || wandBusy || submitting;

  async function writeNewScene() {
    setWandBusy(true);
    setError(undefined);
    try {
      const { data } = await httpClient.post<AiPrompt>(`${mediaUrl}/ai-prompt/scene`, {}, { headers: JSON_HEADERS });
      setLoaded(data);
      setUsage(data.usage);
      setPrompt(data.prompt);
      setFieldError(undefined);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setWandBusy(false);
    }
  }

  function putBackFirst() {
    if (!first) return;
    setLoaded(first);
    setPrompt(first.prompt);
    setFieldError(undefined);
  }

  async function draw() {
    const text = prompt.trim();
    if (!text) {
      setFieldError("Write a prompt first.");
      return;
    }
    if (text.length > MAX_PROMPT_LENGTH) {
      setFieldError(`The prompt must be ${MAX_PROMPT_LENGTH} characters or fewer.`);
      return;
    }
    setFieldError(undefined);
    setSubmitting(true);
    setError(undefined);
    try {
      // The server only records where the first prompt came from; a changed prompt counts as "edited".
      const source: AiPromptSource | "edited" = loaded && text === loaded.prompt.trim() ? loaded.source : "edited";
      const { data } = await httpClient.post<AiJob>(`${mediaUrl}/ai-picture`, { prompt: text, source }, { headers: JSON_HEADERS });
      onStarted(data);
      onClose();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-2">
            <Sparkles className="mt-0.5 h-5 w-5 text-primary" aria-hidden />
            <div>
              <DialogTitle className="text-base font-semibold">AI picture</DialogTitle>
              <DialogDescription className="text-sm text-muted-foreground">
                {word}, sense {senseNumber}
                {pos ? ` (${pos})` : ""}
              </DialogDescription>
            </div>
          </div>
          <DialogClose asChild>
            <Button variant="ghost" size="icon" aria-label="Close" title="Close">
              <X className="h-4 w-4" />
            </Button>
          </DialogClose>
        </div>

        <div className="flex gap-3">
          <div className="flex h-24 w-36 shrink-0 items-center justify-center overflow-hidden rounded-md border border-border bg-muted/40 text-xs text-muted-foreground">
            {media ? (
              media.kind === "clip" ? (
                <video src={media.url} aria-label="Current clip" className="h-full w-full object-cover" muted playsInline />
              ) : (
                <img src={media.url} alt="Current picture" className="h-full w-full object-cover" />
              )
            ) : (
              "No picture"
            )}
          </div>
          <div className="min-w-0 space-y-1 text-sm">
            {meaning && <p>{meaning}</p>}
            {loaded && <p className="text-xs text-muted-foreground">{sourceText(loaded)}</p>}
          </div>
        </div>

        <div className="space-y-1">
          <Textarea
            aria-label="Prompt"
            aria-invalid={fieldError ? true : undefined}
            className="min-h-[220px] font-mono text-xs leading-relaxed"
            value={prompt}
            disabled={loading}
            onChange={(e) => {
              setPrompt(e.target.value);
              setFieldError(undefined);
            }}
          />
          <div className="flex justify-between gap-2 text-xs">
            <span role={fieldError ? "alert" : undefined} className="text-destructive">
              {fieldError}
            </span>
            <span className="text-muted-foreground">
              {prompt.length} / {MAX_PROMPT_LENGTH}
            </span>
          </div>
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            aria-label="Put back the first prompt"
            title="Put back the first prompt"
            disabled={busy || !first}
            onClick={putBackFirst}
          >
            <Undo2 className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Write a new scene with Claude"
            title="Write a new scene with Claude (about $0.02)"
            disabled={busy}
            onClick={() => void writeNewScene()}
          >
            {wandBusy ? <Loader2 role="status" aria-label="Writing a scene" className="h-4 w-4 animate-spin" /> : <WandSparkles className="h-4 w-4" />}
          </Button>
          <span className="min-w-0 flex-1 truncate text-xs text-muted-foreground" aria-label="Today's limits">
            {usage ? usageText(usage) : ""}
          </span>
          <Button variant="outline" size="icon" aria-label="Cancel" title="Cancel" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
          <Button size="icon" aria-label="Draw the picture" title="Draw the picture" disabled={busy} onClick={() => void draw()}>
            {submitting ? <Loader2 role="status" aria-label="Starting" className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function sourceText(prompt: AiPrompt): string {
  if (prompt.source === "scene") return `Prompt from: last media-ai scene${prompt.sceneVersion ? ` (${prompt.sceneVersion})` : ""}`;
  if (prompt.source === "claude") return "Prompt from: new Claude scene";
  return "Prompt from: template";
}

function usageText(usage: AiUsage): string {
  return `today ${usage.picturesToday} of ${usage.picturesPerDay} pictures · $${usage.sceneUsdToday.toFixed(2)} of $${usage.sceneUsdPerDay.toFixed(2)}`;
}

// The backend answers errors as a ProblemDetail: the readable text is in "detail".
function errorText(error: unknown): string {
  const response = (error as { response?: { data?: { detail?: string } } }).response;
  return response?.data?.detail ?? (error as Error).message ?? "Something went wrong.";
}
