import { useState, type ReactNode } from "react";
import { Save, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/input";
import { cn } from "@/lib/utils";

interface Props<T> {
  initial: T;
  // Draws the form fields for the current draft. Call setDraft with a changed copy.
  renderForm: (draft: T, setDraft: (next: T) => void) => ReactNode;
  onSave: (entry: T) => Promise<void> | void;
  onCancel: () => void;
  error?: string;
}

// Edit an entry: a Form tab and a JSON tab over the same draft.
export function EntryEditor<T>({ initial, renderForm, onSave, onCancel, error }: Props<T>) {
  const [tab, setTab] = useState<"form" | "json">("form");
  const [draft, setDraft] = useState<T>(initial);
  const [jsonText, setJsonText] = useState("");
  const [jsonError, setJsonError] = useState<string>();

  function parseJson(): T | undefined {
    try {
      return JSON.parse(jsonText) as T;
    } catch (e) {
      setJsonError(`This is not valid JSON: ${(e as Error).message}`);
      return undefined;
    }
  }

  function openTab(next: "form" | "json") {
    if (next === tab) return;
    if (next === "json") {
      setJsonText(JSON.stringify(draft, null, 2));
    } else {
      const parsed = parseJson();
      if (!parsed) return; // stay on the JSON tab and show the error
      setDraft(parsed);
    }
    setJsonError(undefined);
    setTab(next);
  }

  async function save() {
    const entry = tab === "json" ? parseJson() : draft;
    if (entry) await onSave(entry);
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div role="tablist" className="flex gap-1 rounded-md bg-muted p-1 text-sm">
          {(["form", "json"] as const).map((name) => (
            <button
              key={name}
              role="tab"
              aria-selected={tab === name}
              onClick={() => openTab(name)}
              className={cn("rounded-sm px-3 py-1 font-medium", tab === name ? "bg-card shadow-sm" : "text-muted-foreground")}
            >
              {name === "form" ? "Form" : "JSON"}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          <Button size="icon" aria-label="Save changes" title="Save changes" onClick={save}>
            <Save className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" aria-label="Cancel editing" title="Cancel editing" onClick={onCancel}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {tab === "form" ? (
        renderForm(draft, setDraft)
      ) : (
        <Textarea
          aria-label="Entry JSON"
          className="min-h-[420px] font-mono text-xs"
          value={jsonText}
          onChange={(e) => setJsonText(e.target.value)}
        />
      )}

      {(jsonError || error) && (
        <p role="alert" className="text-sm text-destructive">
          {jsonError ?? error}
        </p>
      )}
    </div>
  );
}

// One labelled field in a form.
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block space-y-1 text-sm">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      {children}
    </label>
  );
}
