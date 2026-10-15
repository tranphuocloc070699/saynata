import { useState } from "react";
import { Check, ImageOff, Pencil, RefreshCw, X } from "lucide-react";
import { Button } from "@/components/ui/button";

interface Props {
  onMarkReviewed: () => void;
  onEdit: () => void;
  onRegenerate: () => void;
  // Words only (TASK-76): the button is shown when this is set, and looks disabled when the word has no media.
  removeMedia?: { word: string; hasMedia: boolean; onConfirm: () => void };
  busy: boolean;
  error?: string;
}

// Icon-only buttons (CLAUDE.md UI rule). Regenerate and Remove all media ask for confirmation in the page.
export function EntryActions({ onMarkReviewed, onEdit, onRegenerate, removeMedia, busy, error }: Props) {
  const [confirming, setConfirming] = useState<"regenerate" | "media" | null>(null);

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <Button variant="outline" size="icon" aria-label="Mark reviewed" title="Mark reviewed" disabled={busy} onClick={onMarkReviewed}>
          <Check className="h-4 w-4" />
        </Button>
        <Button variant="outline" size="icon" aria-label="Edit" title="Edit" disabled={busy} onClick={onEdit}>
          <Pencil className="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="icon"
          aria-label="Regenerate"
          title="Regenerate"
          disabled={busy}
          onClick={() => setConfirming("regenerate")}
        >
          <RefreshCw className="h-4 w-4" />
        </Button>
        {removeMedia && (
          <Button
            variant="outline"
            size="icon"
            aria-label="Remove all media"
            title="Remove all media"
            disabled={busy || !removeMedia.hasMedia}
            onClick={() => setConfirming("media")}
          >
            <ImageOff className="h-4 w-4" />
          </Button>
        )}
      </div>

      {confirming === "regenerate" && (
        <div role="alertdialog" aria-label="Confirm regenerate" className="flex items-center gap-3 rounded-md border border-destructive/40 bg-card p-3 text-sm">
          <p className="flex-1">
            Delete this entry? It is made again by Claude the next time someone asks for it. This costs a little.
          </p>
          <Button
            variant="destructive"
            size="icon"
            aria-label="Confirm regenerate"
            onClick={() => {
              setConfirming(null);
              onRegenerate();
            }}
          >
            <Check className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" aria-label="Cancel regenerate" onClick={() => setConfirming(null)}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {confirming === "media" && removeMedia && (
        <div role="alertdialog" aria-label="Confirm remove all media" className="flex items-center gap-3 rounded-md border border-destructive/40 bg-card p-3 text-sm">
          <p className="flex-1">
            Remove the pictures and clips of all senses of {removeMedia.word}? The app stops showing them.
          </p>
          <Button
            variant="destructive"
            size="icon"
            aria-label="Confirm remove all media"
            onClick={() => {
              setConfirming(null);
              removeMedia.onConfirm();
            }}
          >
            <Check className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" aria-label="Cancel remove all media" onClick={() => setConfirming(null)}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
