import { useState } from "react";
import { Check, Pencil, RefreshCw, X } from "lucide-react";
import { Button } from "@/components/ui/button";

interface Props {
  onMarkReviewed: () => void;
  onEdit: () => void;
  onRegenerate: () => void;
  busy: boolean;
  error?: string;
}

// Three icon-only buttons (CLAUDE.md UI rule). Regenerate asks for confirmation in the page.
export function EntryActions({ onMarkReviewed, onEdit, onRegenerate, busy, error }: Props) {
  const [confirming, setConfirming] = useState(false);

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
          onClick={() => setConfirming(true)}
        >
          <RefreshCw className="h-4 w-4" />
        </Button>
      </div>

      {confirming && (
        <div role="alertdialog" aria-label="Confirm regenerate" className="flex items-center gap-3 rounded-md border border-destructive/40 bg-card p-3 text-sm">
          <p className="flex-1">
            Delete this entry? It is made again by Claude the next time someone asks for it. This costs a little.
          </p>
          <Button
            variant="destructive"
            size="icon"
            aria-label="Confirm regenerate"
            onClick={() => {
              setConfirming(false);
              onRegenerate();
            }}
          >
            <Check className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" aria-label="Cancel regenerate" onClick={() => setConfirming(false)}>
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
