import { useApiUrl, useCustomMutation, useDelete, useInfiniteList, useInvalidate, useUpdate, type CrudFilter } from "@refinedev/core";
import { Loader2, Search } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { EntryActions } from "@/components/EntryActions";
import { EntryEditor } from "@/components/EntryEditor";
import { FieldTable, type FieldRow } from "@/components/FieldTable";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 50;

export interface Item {
  id: number | string;
  needsReview?: boolean;
}

export interface Chip<T> {
  id: string;
  label: string;
  // "server" chips are sent to the backend as a filter. "client" chips filter the loaded page.
  server?: { field: string; value: string | boolean };
  client?: { group: string; matches: (item: T) => boolean };
}

interface Props<T extends Item, E> {
  resource: "words" | "sentences";
  chips: Chip<T>[];
  itemTitle: (item: T) => string;
  itemSubtitle: (item: T) => string;
  itemBadges?: (item: T) => ReactNode;
  detailRows: (item: T) => FieldRow[];
  // The JSON the backend stores for this item: what the editor changes and PUT sends.
  editableEntry: (item: T) => E;
  renderForm: (draft: E, setDraft: (next: E) => void, item: T) => ReactNode;
}

// Layout A: list with search and chips on the left, the selected entry as a table on the right.
export function EntryBrowser<T extends Item, E>({
  resource,
  chips,
  itemTitle,
  itemSubtitle,
  itemBadges,
  detailRows,
  editableEntry,
  renderForm,
}: Props<T, E>) {
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [activeChips, setActiveChips] = useState<string[]>([]);
  const [selectedId, setSelectedId] = useState<T["id"]>();
  const [editing, setEditing] = useState(false);
  const [actionError, setActionError] = useState<string>();

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
  for (const chip of chosen) {
    if (chip.server) filters.push({ field: chip.server.field, operator: "eq", value: chip.server.value });
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

  // Client chips: same group = any may match (A1-A2 or B1-B2), different groups must all match.
  const clientGroups = new Map<string, Chip<T>[]>();
  for (const chip of chosen) {
    if (chip.client) clientGroups.set(chip.client.group, [...(clientGroups.get(chip.client.group) ?? []), chip]);
  }
  const items = loaded.filter((item) =>
    [...clientGroups.values()].every((group) => group.some((chip) => chip.client!.matches(item as unknown as T))),
  ) as unknown as T[];
  const total = pages[0]?.total ?? 0;

  // When the sentinel row at the end of the list comes into view, load the next page.
  // It watches again after every page, so a short page (for example with a client chip) keeps loading until the list is full.
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
      await invalidate({ resource, invalidates: ["list", "detail"] });
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
  const save = (entry: E) =>
    run(
      () => update({ resource, id: selectedId!, values: entry as object, meta: { method: "put" } }),
      () => setEditing(false),
    );

  function toggleChip(id: string) {
    setActiveChips((current) => (current.includes(id) ? current.filter((c) => c !== id) : [...current, id]));
    listRef.current?.scrollTo?.({ top: 0 });
  }

  return (
    <div className="grid h-full grid-cols-[minmax(280px,380px)_1fr]">
      <section aria-label="List" className="flex min-h-0 flex-col border-r border-border">
        <div className="space-y-3 border-b border-border p-4">
          <div className="relative">
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
          <div className="flex flex-wrap gap-2" role="group" aria-label="Filters">
            {chips.map((chip) => (
              <button
                key={chip.id}
                aria-pressed={activeChips.includes(chip.id)}
                onClick={() => toggleChip(chip.id)}
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

        <ul ref={listRef} className="min-h-0 flex-1 overflow-y-auto">
          {listQuery.isLoading && <li className="p-4 text-sm text-muted-foreground">Loading...</li>}
          {listQuery.isError && <li className="p-4 text-sm text-destructive">Could not load the list.</li>}
          {!listQuery.isLoading && !listQuery.isError && items.length === 0 && (
            <li className="p-4 text-sm text-muted-foreground">Nothing found.</li>
          )}
          {items.map((item) => (
            <li key={item.id}>
              <button
                aria-pressed={item.id === selectedId}
                onClick={() => {
                  setSelectedId(item.id);
                  setEditing(false);
                  setActionError(undefined);
                }}
                className={cn(
                  "block w-full border-b border-border px-4 py-3 text-left hover:bg-muted",
                  item.id === selectedId && "bg-muted",
                )}
              >
                <div className="flex items-center gap-2">
                  <span className="font-semibold">{itemTitle(item)}</span>
                  {item.needsReview && <Badge variant="warning">Needs review</Badge>}
                  {itemBadges?.(item)}
                </div>
                <p className="mt-0.5 line-clamp-2 text-sm text-muted-foreground">{itemSubtitle(item)}</p>
              </button>
            </li>
          ))}
          {hasNextPage && (
            <li ref={sentinelRef} className="flex justify-center p-3" aria-hidden={!isFetchingNextPage}>
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

      <section aria-label="Entry" className="min-h-0 overflow-y-auto p-6">
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
              />
            </div>
            <FieldTable rows={detailRows(selected)} />
          </div>
        )}
      </section>
    </div>
  );
}
