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

// Edit an entry: a Form tab and a JSON tab over the same draft. Isomorphic form look (TASK-99.4): underlined tabs,
// sections with a title on the left, and Cancel and Save at the bottom, where they stay while the form scrolls.
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
    <div className="space-y-4">
      <div role="tablist" className="flex gap-6 border-b border-border text-sm">
        {(["form", "json"] as const).map((name) => (
          <button
            key={name}
            role="tab"
            aria-selected={tab === name}
            onClick={() => openTab(name)}
            className={cn(
              "-mb-px border-b-2 py-2 font-medium",
              tab === name ? "border-foreground text-foreground" : "border-transparent text-muted-foreground hover:text-foreground",
            )}
          >
            {name === "form" ? "Form" : "JSON"}
          </button>
        ))}
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

      <div className="sticky -bottom-6 -mx-6 flex justify-end gap-2 border-t border-border bg-card px-6 pb-6 pt-3">
        <Button variant="outline" size="icon" aria-label="Cancel editing" title="Cancel editing" onClick={onCancel}>
          <X className="h-4 w-4" />
        </Button>
        <Button size="icon" aria-label="Save changes" title="Save changes" onClick={save}>
          <Save className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}

// One part of a form: a title and a short hint on the left, the fields on the right (Isomorphic form layout).
export function FormSection({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="grid gap-4 border-b border-dashed border-border pb-5 last:border-b-0 md:grid-cols-[170px_1fr]">
      <div>
        <h3 className="text-sm font-semibold">{title}</h3>
        {hint && <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{hint}</p>}
      </div>
      <div className="min-w-0 space-y-3">{children}</div>
    </section>
  );
}

// One labelled field in a form.
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block space-y-1.5 text-sm">
      <span className="text-[13px] font-medium">{label}</span>
      {children}
    </label>
  );
}
