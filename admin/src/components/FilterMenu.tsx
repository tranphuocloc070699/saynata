import { useList, type CrudFilter } from "@refinedev/core";
import { ChevronDown, ListFilter, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { Chip } from "@/components/EntryBrowser";
import { cn } from "@/lib/utils";

// The backend filter of one chip on its own, for its count. A chip that combines with others ("any") still counts
// only its own values here, so A1-A2 shows the A1 and A2 words, not the whole list.
export function chipFilter(chip: Chip): CrudFilter {
  return Array.isArray(chip.value)
    ? { field: chip.field, operator: "in", value: chip.value }
    : { field: chip.field, operator: "eq", value: chip.value };
}

// How many entries one choice has: the list endpoint with one row asked (_end=1), read from X-Total-Count.
// The count lives under the same resource as the list, so every action that reloads the list (invalidates "all")
// reloads the counts too (TASK-101).
function Count({ resource, filters }: { resource: string; filters: CrudFilter[] }) {
  const { result, query } = useList({ resource, pagination: { currentPage: 1, pageSize: 1 }, filters });
  return (
    <span className="min-w-8 rounded-full bg-muted px-2 py-0.5 text-center text-xs tabular-nums text-muted-foreground">
      {query.isLoading ? "…" : query.isError ? "-" : (result.total ?? 0).toLocaleString("en-US")}
    </span>
  );
}

interface Props {
  resource: string;
  chips: Chip[];
  active: string[];
  onToggle: (chip: Chip) => void;
  // The queue rows of the Pending choice (TASK-100): kind word or sentence. Pages without Pending leave it out.
  pendingKind?: "word" | "sentence";
}

// One Filter button with a menu of parent groups and their choices (TASK-101, mockup 2026-10-23).
// A group with combine "any" uses check boxes (Level), "one" uses round buttons (Source). Every choice has a count.
// The chosen ones show as tags next to the button, each with an icon-only button to remove it.
export function FilterMenu({ resource, chips, active, onToggle, pendingKind }: Props) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  // Close on a click outside or Esc.
  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => box.current?.contains(e.target as Node) || setOpen(false);
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const groups = [...new Set(chips.map((chip) => chip.group))];
  const chosen = chips.filter((chip) => active.includes(chip.id));

  return (
    <div className="flex flex-wrap items-center gap-2">
      <div ref={box} className="relative">
        <button
          type="button"
          aria-expanded={open}
          aria-haspopup="true"
          onClick={() => setOpen((v) => !v)}
          className={cn(
            "inline-flex h-9 items-center gap-2 rounded-lg border bg-card px-3 text-sm font-medium",
            chosen.length ? "border-primary text-foreground" : "border-border text-muted-foreground hover:bg-muted",
          )}
        >
          <ListFilter className="h-4 w-4" aria-hidden />
          Filter
          {chosen.length > 0 && (
            <span className="rounded-full bg-primary px-1.5 text-xs text-primary-foreground">{chosen.length}</span>
          )}
          <ChevronDown className="h-3 w-3" aria-hidden />
        </button>
        {open && (
          <div
            role="group"
            aria-label="Filter choices"
            className="absolute left-0 top-11 z-30 w-80 rounded-xl border border-border bg-card p-2 shadow-xl"
          >
            {groups.map((group, g) => {
              const members = chips.filter((chip) => chip.group === group);
              const many = members.some((chip) => chip.combine === "any");
              return (
                <div key={group} className={cn(g > 0 && "mt-1 border-t border-border/60 pt-1")}>
                  <p className="flex justify-between px-2.5 pb-1 pt-2 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                    {group}
                    {!many && members.length > 1 && <span>one at a time</span>}
                  </p>
                  {members.map((chip) => {
                    const on = active.includes(chip.id);
                    return (
                      <button
                        key={chip.id}
                        type="button"
                        aria-pressed={on}
                        aria-label={chip.label}
                        onClick={() => onToggle(chip)}
                        className="grid w-full grid-cols-[16px_1fr_auto] items-start gap-2.5 rounded-lg px-2.5 py-2 text-left text-sm hover:bg-muted"
                      >
                        <span
                          aria-hidden
                          className={cn(
                            "mt-0.5 h-4 w-4 border-[1.5px]",
                            many ? "rounded" : "rounded-full",
                            on ? "border-primary bg-primary shadow-[inset_0_0_0_3px_white]" : "border-muted-foreground/50",
                          )}
                        />
                        <span>
                          {chip.label}
                          {chip.description && <span className="mt-0.5 block text-xs text-muted-foreground">{chip.description}</span>}
                        </span>
                        <Count
                          resource={chip.pending ? "prefill/items" : resource}
                          filters={
                            chip.pending
                              ? [
                                  { field: "kind", operator: "eq", value: pendingKind ?? "word" },
                                  { field: "status", operator: "eq", value: "pending" },
                                ]
                              : [chipFilter(chip)]
                          }
                        />
                      </button>
                    );
                  })}
                </div>
              );
            })}
          </div>
        )}
      </div>
      {chosen.map((chip) => (
        <span key={chip.id} className="inline-flex items-center gap-1 rounded-full bg-accent py-1 pl-3 pr-1 text-xs font-medium text-accent-foreground">
          {chip.label}
          <button
            type="button"
            aria-label={`Remove filter ${chip.label}`}
            title={`Remove filter ${chip.label}`}
            onClick={() => onToggle(chip)}
            className="grid h-5 w-5 place-items-center rounded-full bg-card hover:bg-muted"
          >
            <X className="h-3 w-3" />
          </button>
        </span>
      ))}
    </div>
  );
}
