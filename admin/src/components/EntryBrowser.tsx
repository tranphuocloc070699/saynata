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
import { FilterMenu } from "@/components/FilterMenu";
import { PendingQueue } from "@/components/PendingQueue";
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
  // The parent in the Filter menu, for example "Level" (TASK-101).
  group: string;
  // A short line under the choice, for example "On the phone, works offline".
  description?: string;
  // The Pending choice: shows the prefill queue rows instead of the list (TASK-101). It is never sent as a filter,
  // and it is on alone: choosing it turns the other choices off, and another choice turns it off.
  pending?: boolean;
}

// One column of the table view (TASK-99.2). The first column is always the item: thumbnail, title, subtitle.
export interface Column<T> {
  header: string;
  cell: (item: T) => ReactNode;
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
  // When set, the list view is a table with these columns and a row opens the entry in a right drawer
  // (Isomorphic list page, TASK-99.2). Without it the list view is the split view (list left, entry right).
  columns?: Column<T>[];
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
  columns,
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

  const chosen = chips.filter((chip) => activeChips.includes(chip.id) && !chip.pending);
  const pendingOn = chips.some((chip) => chip.pending && activeChips.includes(chip.id));
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
  // The scroll box: the <ul> in the split and grid views, the box around the <table> in the table view.
  const listRef = useRef<HTMLElement | null>(null);
  const sentinelRef = useRef<HTMLElement | null>(null);
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
  const table = !grid && !!columns;
  // The grid and the table use the whole width: the entry opens in a drawer on the right.
  const drawer = grid || table;
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

  function select(item: T) {
    setSelectedId(item.id);
    setEditing(false);
    setActionError(undefined);
  }

  function toggleChip(chip: Chip) {
    setActiveChips((current) => {
      if (current.includes(chip.id)) return current.filter((id) => id !== chip.id);
      if (chip.pending) return [chip.id];
      // A chip that cannot be combined replaces the other chips of the same field; any chip turns Pending off.
      const keep = current.filter((id) => {
        const other = chips.find((c) => c.id === id);
        return !other?.pending && (chip.combine === "any" || other?.field !== chip.field);
      });
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
    <div className={cn("grid h-full", drawer ? "grid-cols-1" : "grid-cols-[minmax(280px,380px)_1fr]")}>
      <section aria-label="List" className={cn("flex min-h-0 flex-col", !drawer && "border-r border-border")}>
        <div className="border-b border-border p-4">
          <div className="flex flex-wrap items-center gap-2">
            <div className={cn("relative flex-1", drawer && "max-w-sm")}>
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
            <div role="group" aria-label="Filters">
              <FilterMenu
                resource={resource}
                chips={chips}
                active={activeChips}
                onToggle={toggleChip}
                pendingKind={resource === "words" ? "word" : "sentence"}
              />
            </div>
            {itemThumb && (
              <div role="group" aria-label="View" className="ml-auto flex shrink-0 overflow-hidden rounded-md border border-border">
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
        </div>

        {pendingOn ? (
          <PendingQueue kind={resource === "words" ? "word" : "sentence"} query={query} />
        ) : table ? (
          <div ref={(node) => void (listRef.current = node)} className="min-h-0 flex-1 overflow-y-auto">
            <table aria-label="Rows" className="w-full border-collapse text-sm">
              <thead className="sticky top-0 z-10 bg-muted">
                <tr>
                  {["", ...columns!.map((column) => column.header)].map((header, i) => (
                    <th
                      key={i}
                      scope="col"
                      className="border-b border-border px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-wider text-muted-foreground"
                    >
                      {i === 0 ? (resource === "words" ? "Word" : "Sentence") : header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {listQuery.isLoading && (
                  <tr>
                    <td colSpan={columns!.length + 1} className="p-4 text-muted-foreground">Loading...</td>
                  </tr>
                )}
                {listQuery.isError && (
                  <tr>
                    <td colSpan={columns!.length + 1} className="p-4 text-destructive">Could not load the list.</td>
                  </tr>
                )}
                {!listQuery.isLoading && !listQuery.isError && items.length === 0 && (
                  <tr>
                    <td colSpan={columns!.length + 1} className="p-4 text-muted-foreground">Nothing found.</td>
                  </tr>
                )}
                {items.map((item) => (
                  <tr
                    key={item.id}
                    // The whole row opens the entry; the first cell's button is the one for keyboards and screen readers.
                    onClick={() => select(item)}
                    onPointerEnter={(e) => e.pointerType !== "touch" && setHoveredId(item.id)}
                    onPointerLeave={() => setHoveredId((current) => (current === item.id ? undefined : current))}
                    className={cn("cursor-pointer border-b border-border/60 hover:bg-muted/60", item.id === selectedId && "bg-accent/60")}
                  >
                    <td className="px-4 py-2.5">
                      <button
                        type="button"
                        aria-pressed={item.id === selectedId}
                        onClick={(e) => {
                          e.stopPropagation();
                          select(item);
                        }}
                        className="flex items-center gap-3 text-left"
                      >
                        {itemThumb?.(item, hoveredId === item.id, "row")}
                        <span className="min-w-0">
                          <span className="block font-semibold">{itemTitle(item)}</span>
                          <span className="line-clamp-1 text-muted-foreground">{itemSubtitle(item)}</span>
                        </span>
                      </button>
                    </td>
                    {columns!.map((column) => (
                      <td key={column.header} className="px-4 py-2.5">
                        <div className="flex flex-wrap items-center gap-1">{column.cell(item)}</div>
                      </td>
                    ))}
                  </tr>
                ))}
                {hasNextPage && (
                  <tr ref={(node) => void (sentinelRef.current = node)} aria-hidden={!isFetchingNextPage}>
                    <td colSpan={columns!.length + 1} className="p-3 text-center">
                      {isFetchingNextPage && (
                        <Loader2 role="status" aria-label="Loading more" className="mx-auto h-4 w-4 animate-spin text-muted-foreground" />
                      )}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        ) : (
          <ul
            ref={(node) => void (listRef.current = node)}
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
                  onClick={() => select(item)}
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
                          {/* The page decides the badges (Words: only "Needs review · N"); without it, the plain flag. */}
                          {itemBadges ? itemBadges(item) : item.needsReview && <Badge variant="warning">Needs review</Badge>}
                        </div>
                      </div>
                    </>
                  ) : (
                    <div className="flex items-center gap-3">
                      {itemThumb?.(item, hoveredId === item.id, "row")}
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold">{itemTitle(item)}</span>
                          {/* The page decides the badges (Words: only "Needs review · N"); without it, the plain flag. */}
                          {itemBadges ? itemBadges(item) : item.needsReview && <Badge variant="warning">Needs review</Badge>}
                        </div>
                        <p className="mt-0.5 line-clamp-2 text-sm text-muted-foreground">{itemSubtitle(item)}</p>
                      </div>
                    </div>
                  )}
                </button>
              </li>
            ))}
            {hasNextPage && (
              <li ref={(node) => void (sentinelRef.current = node)} className="col-span-full flex justify-center p-3" aria-hidden={!isFetchingNextPage}>
                {isFetchingNextPage && <Loader2 role="status" aria-label="Loading more" className="h-4 w-4 animate-spin text-muted-foreground" />}
              </li>
            )}
          </ul>
        )}

        {!pendingOn && (
        <div className="border-t border-border p-3 text-sm text-muted-foreground">
          <span>
            {loaded.length.toLocaleString("en-US")} of {total.toLocaleString("en-US")}
          </span>
        </div>
        )}
      </section>

      {drawer ? (
        // The grid and the table take the whole width: the entry opens in a drawer on the right.
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
