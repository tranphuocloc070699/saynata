import { useApiUrl, useCustomMutation, useDelete, useInfiniteList, useInvalidate, useUpdate, type CrudFilter } from "@refinedev/core";
import { LayoutGrid, List, Loader2, Search } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { useSearchParams } from "react-router";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Sheet } from "@/components/ui/sheet";
import { EntryActions } from "@/components/EntryActions";
import { EntryEditor } from "@/components/EntryEditor";
import { FieldTable, type FieldRow } from "@/components/FieldTable";
import { httpClient } from "@/providers/dataProvider";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 50;

export interface Item {
  id: number | string;
  needsReview?: boolean;
}

// Every chip is a filter the backend applies (a query parameter). Nothing is filtered on the client,
// because with infinite scroll the client only holds the pages loaded so far.
export interface Chip {
  id: string;
  label: string;
  field: string; // the query parameter, for example "source" or "cefr"
  value: string | boolean | string[];
  // Chips with the same field: "one" (default) = turning one on turns the others off.
  // "any" = they can be on together and their values are OR-ed into one list (A1-A2 and B1-B2).
  combine?: "one" | "any";
}

interface Props<T extends Item, E> {
  resource: "words" | "sentences";
  chips: Chip[];
  itemTitle: (item: T) => string;
  itemSubtitle: (item: T) => string;
  // A small picture on the left of the row (words list). Without it the row is two lines of text.
  // hovered is true while the mouse is over the row (a mouse, not a finger): the words list plays a clip then.
  // variant is "row" in the list and "card" in the grid (a bigger picture, TASK-81).
  // When this is set, the header also gets the list/grid toggle.
  itemThumb?: (item: T, hovered: boolean, variant: "row" | "card") => ReactNode;
  itemBadges?: (item: T) => ReactNode;
  // Words only (TASK-76): when set, the header gets a Remove all media button. It is disabled when this says false.
  hasMedia?: (item: T) => boolean;
  detailRows: (item: T) => FieldRow[];
  // The JSON the backend stores for this item: what the editor changes and PUT sends.
  editableEntry: (item: T) => E;
  renderForm: (draft: E, setDraft: (next: E) => void, item: T) => ReactNode;
}

// The list/grid choice is remembered in the browser. Storage can be blocked (private window): then the list is used.
export type ViewMode = "list" | "grid";
export const viewKey = (resource: string) => `admin.${resource}.view`;

function readView(resource: string): ViewMode {
  try {
    return localStorage.getItem(viewKey(resource)) === "grid" ? "grid" : "list";
  } catch {
    return "list";
  }
}

// Layout A: list with search and chips on the left, the selected entry as a table on the right.
export function EntryBrowser<T extends Item, E>({
  resource,
  chips,
  itemTitle,
  itemSubtitle,
  itemThumb,
  itemBadges,
  hasMedia,
  detailRows,
  editableEntry,
  renderForm,
}: Props<T, E>) {
  const [view, setView] = useState<ViewMode>(() => readView(resource));
  // The top bar search opens /words?q=...: that text becomes this list's search.
  const [params] = useSearchParams();
  const urlQuery = params.get("q") ?? "";
  const [search, setSearch] = useState(urlQuery);
  const [query, setQuery] = useState(urlQuery.trim());
  const [activeChips, setActiveChips] = useState<string[]>([]);
  const [selectedId, setSelectedId] = useState<T["id"]>();
  const [editing, setEditing] = useState(false);
  const [actionError, setActionError] = useState<string>();
  // Only one row is hovered at a time (the words list plays that row's clip).
  const [hoveredId, setHoveredId] = useState<T["id"]>();

  useEffect(() => setSearch(urlQuery), [urlQuery]);

  // Wait a moment after typing before asking the backend.
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(search.trim());
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const chosen = chips.filter((chip) => activeChips.includes(chip.id));
  const filters: CrudFilter[] = [];
  if (query) filters.push({ field: "q", operator: "contains", value: query });
  for (const field of new Set(chosen.map((chip) => chip.field))) {
    const sameField = chosen.filter((chip) => chip.field === field);
    if (sameField[0].combine === "any") {
      // Levels are OR-ed: the backend gets one list, for example cefr=A1&cefr=A2&cefr=B1&cefr=B2.
      const values = sameField.flatMap((chip) => chip.value as string[]);
      filters.push({ field, operator: "in", value: [...new Set(values)] });
    } else {
      filters.push({ field, operator: "eq", value: sameField[0].value as string | boolean });
    }
  }

  // The query key holds the filters, so a new search or chip starts again at page 1.
  const { query: listQuery, result } = useInfiniteList<T & Record<string, unknown>>({
    resource,
    pagination: { currentPage: 1, pageSize: PAGE_SIZE },
    filters,
    queryOptions: {
      // The backend sends a total, not a cursor: there is a next page while we hold fewer rows than the total.
      getNextPageParam: (lastPage, allPages) =>
        allPages.reduce((count, page) => count + page.data.length, 0) < (lastPage.total ?? 0) ? allPages.length + 1 : undefined,
    },
  });
  const pages = result.data?.pages ?? [];
  const loaded = pages.flatMap((page) => page.data);
  const hasNextPage = !!result.hasNextPage;
  const { fetchNextPage, isFetchingNextPage } = listQuery;

  const items = loaded as unknown as T[];
  // The backend counts only the rows that match the search and the chips.
  const total = pages[0]?.total ?? 0;

  // When the sentinel row at the end of the list comes into view, load the next page.
  // It watches again after every page, so a short page keeps loading until the list is full.
  const listRef = useRef<HTMLUListElement>(null);
  const sentinelRef = useRef<HTMLLIElement>(null);
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasNextPage || isFetchingNextPage || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) fetchNextPage();
      },
      { root: listRef.current, rootMargin: "200px" },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, loaded.length, fetchNextPage]);

  const grid = view === "grid" && !!itemThumb;
  const selected = items.find((item) => item.id === selectedId);

  const apiUrl = useApiUrl();
  const invalidate = useInvalidate();
  const { mutateAsync: update } = useUpdate();
  const { mutateAsync: remove } = useDelete();
  const { mutateAsync: post } = useCustomMutation();
  const [busy, setBusy] = useState(false);

  async function run(action: () => Promise<unknown>, afterwards?: () => void) {
    setBusy(true);
    setActionError(undefined);
    try {
      await action();
      // "all", not "list": the list is an infinite list, which Refine keeps under its own key ("infinite").
      // With "list" the old rows stayed on screen after a save, a delete or the check button (TASK-57).
      await invalidate({ resource, invalidates: ["all"] });
      afterwards?.();
    } catch (error) {
      setActionError((error as Error).message || "Something went wrong.");
    } finally {
      setBusy(false);
    }
  }

  const markReviewed = () =>
    run(() => post({ url: `${apiUrl}/${resource}/${selectedId}/reviewed`, method: "post", values: {} }));
  const regenerate = () => run(() => remove({ resource, id: selectedId! }), () => setSelectedId(undefined));
  // DELETE /words/{id}/media. The backend needs a JSON Content-Type on every write. run() reads the list again.
  const removeMedia = () =>
    run(() =>
      httpClient.delete(`${apiUrl}/${resource}/${selectedId}/media`, { data: {}, headers: { "Content-Type": "application/json" } }),
    );
  const save = (entry: E) =>
    run(
      () => update({ resource, id: selectedId!, values: entry as object, meta: { method: "put" } }),
      () => setEditing(false),
    );

  function chooseView(next: ViewMode) {
    setView(next);
    try {
      localStorage.setItem(viewKey(resource), next);
    } catch {
      // not saved; the choice still works until the page is closed
    }
  }

  function toggleChip(chip: Chip) {
    setActiveChips((current) => {
      if (current.includes(chip.id)) return current.filter((id) => id !== chip.id);
      // A chip that cannot be combined replaces the other chips of the same field.
      const keep =
        chip.combine === "any" ? current : current.filter((id) => chips.find((c) => c.id === id)?.field !== chip.field);
      return [...keep, chip.id];
    });
    listRef.current?.scrollTo?.({ top: 0 });
  }

  const entryPane = (
    <>
      {!selected ? (
        <p className="text-sm text-muted-foreground">Pick an entry from the list.</p>
      ) : editing ? (
        <EntryEditor
          key={String(selected.id)}
          initial={editableEntry(selected)}
          renderForm={(draft, setDraft) => renderForm(draft, setDraft, selected)}
          onSave={save}
          onCancel={() => setEditing(false)}
          error={actionError}
        />
      ) : (
        <div className="space-y-4">
          <div className="flex items-start justify-between gap-4">
            <h2 className="text-2xl font-semibold">{itemTitle(selected)}</h2>
            <EntryActions
              busy={busy}
              error={actionError}
              onMarkReviewed={markReviewed}
              onEdit={() => setEditing(true)}
              onRegenerate={regenerate}
              removeMedia={hasMedia ? { word: itemTitle(selected), hasMedia: hasMedia(selected), onConfirm: removeMedia } : undefined}
            />
          </div>
          <FieldTable rows={detailRows(selected)} />
        </div>
      )}
    </>
  );

  return (
    <div className={cn("grid h-full", grid ? "grid-cols-1" : "grid-cols-[minmax(280px,380px)_1fr]")}>
      <section aria-label="List" className={cn("flex min-h-0 flex-col", !grid && "border-r border-border")}>
        <div className="space-y-3 border-b border-border p-4">
          <div className="flex items-center gap-2">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
              <Input
                type="search"
                aria-label="Search"
                placeholder="Search"
                className="pl-9"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            {itemThumb && (
              <div role="group" aria-label="View" className="flex shrink-0 overflow-hidden rounded-md border border-border">
                {(
                  [
                    ["list", "List view", List],
                    ["grid", "Grid view", LayoutGrid],
                  ] as const
                ).map(([mode, label, Icon]) => (
                  <button
                    key={mode}
                    type="button"
                    aria-label={label}
                    title={label}
                    aria-pressed={view === mode}
                    onClick={() => chooseView(mode)}
                    className={cn(
                      "inline-flex h-10 w-10 items-center justify-center",
                      view === mode ? "bg-accent text-accent-foreground" : "bg-card text-muted-foreground hover:bg-muted",
                    )}
                  >
                    <Icon className="h-4 w-4" />
                  </button>
                ))}
              </div>
            )}
          </div>
          <div className="flex flex-wrap gap-2" role="group" aria-label="Filters">
            {chips.map((chip) => (
              <button
                key={chip.id}
                aria-pressed={activeChips.includes(chip.id)}
                onClick={() => toggleChip(chip)}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs font-medium",
                  activeChips.includes(chip.id) ? "border-primary bg-accent text-accent-foreground" : "border-border bg-card text-muted-foreground",
                )}
              >
                {chip.label}
              </button>
            ))}
          </div>
        </div>

        <ul
          ref={listRef}
          aria-label={grid ? "Grid" : undefined}
          className={cn(
            "min-h-0 flex-1 overflow-y-auto",
            grid && "grid content-start gap-3 p-4 [grid-template-columns:repeat(auto-fill,minmax(160px,1fr))]",
          )}
        >
          {listQuery.isLoading && <li className="col-span-full p-4 text-sm text-muted-foreground">Loading...</li>}
          {listQuery.isError && <li className="col-span-full p-4 text-sm text-destructive">Could not load the list.</li>}
          {!listQuery.isLoading && !listQuery.isError && items.length === 0 && (
            <li className="col-span-full p-4 text-sm text-muted-foreground">Nothing found.</li>
          )}
          {items.map((item) => (
            <li key={item.id}>
              <button
                aria-pressed={item.id === selectedId}
                // A finger tap also sends mouse events on a phone; only a real mouse or pen counts as hover.
                onPointerEnter={(e) => e.pointerType !== "touch" && setHoveredId(item.id)}
                onPointerLeave={() => setHoveredId((current) => (current === item.id ? undefined : current))}
                onClick={() => {
                  setSelectedId(item.id);
                  setEditing(false);
                  setActionError(undefined);
                }}
                className={
                  grid
                    ? cn(
                        "block h-full w-full space-y-2 rounded-lg border border-border bg-card p-2 text-left hover:bg-muted",
                        item.id === selectedId && "outline outline-2 outline-primary",
                      )
                    : cn("block w-full border-b border-border px-4 py-3 text-left hover:bg-muted", item.id === selectedId && "bg-muted")
                }
              >
                {grid ? (
                  <>
                    {itemThumb?.(item, hoveredId === item.id, "card")}
                    <div className="min-w-0 space-y-1 px-1 pb-1">
                      <span className="block truncate font-semibold">{itemTitle(item)}</span>
                      <p className="line-clamp-1 text-sm text-muted-foreground">{itemSubtitle(item)}</p>
                      <div className="flex flex-wrap items-center gap-1">
                        {item.needsReview && <Badge variant="warning">Needs review</Badge>}
                        {itemBadges?.(item)}
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="flex items-center gap-3">
                    {itemThumb?.(item, hoveredId === item.id, "row")}
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-semibold">{itemTitle(item)}</span>
                        {item.needsReview && <Badge variant="warning">Needs review</Badge>}
                        {itemBadges?.(item)}
                      </div>
                      <p className="mt-0.5 line-clamp-2 text-sm text-muted-foreground">{itemSubtitle(item)}</p>
                    </div>
                  </div>
                )}
              </button>
            </li>
          ))}
          {hasNextPage && (
            <li ref={sentinelRef} className="col-span-full flex justify-center p-3" aria-hidden={!isFetchingNextPage}>
              {isFetchingNextPage && <Loader2 role="status" aria-label="Loading more" className="h-4 w-4 animate-spin text-muted-foreground" />}
            </li>
          )}
        </ul>

        <div className="border-t border-border p-3 text-sm text-muted-foreground">
          <span>
            {loaded.length.toLocaleString("en-US")} of {total.toLocaleString("en-US")}
          </span>
        </div>
      </section>

      {grid ? (
        // The grid takes the whole width: the entry opens in a drawer on the right.
        <Sheet open={!!selected} onClose={() => setSelectedId(undefined)} title="Entry details">
          <section aria-label="Entry">{entryPane}</section>
        </Sheet>
      ) : (
        <section aria-label="Entry" className="min-h-0 overflow-y-auto p-6">
          {entryPane}
        </section>
      )}
    </div>
  );
}
