import { useApiUrl, useInfiniteList, useInvalidate } from "@refinedev/core";
import { Check, Loader2, Trash2, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { PrefillQueueItem } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { httpClient } from "@/providers/dataProvider";

const PAGE_SIZE = 50;
const RESOURCE = "prefill/items";

// Where a queue row came from, in plain words.
const SOURCES: Record<string, string> = {
  "sentence-words": "In a sentence",
  "common-sentences": "Common sentences",
  "scowl-70": "Common words",
  "oewn-2025": "Phrases",
};

interface Props {
  kind: "word" | "sentence";
  query: string;
}

// The Pending choice (TASK-101): words and sentences in the prefill queue (TASK-100), not in the database yet.
// The night job answers them; then they leave this list and show up as normal rows. A row can be taken out of the
// queue (the server keeps it as "skipped", so the same word is not queued again).
export function PendingQueue({ kind, query }: Props) {
  const apiUrl = useApiUrl();
  const invalidate = useInvalidate();
  const [confirming, setConfirming] = useState<number>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  const filters = [
    { field: "kind", operator: "eq" as const, value: kind },
    { field: "status", operator: "eq" as const, value: "pending" },
    ...(query ? [{ field: "q", operator: "contains" as const, value: query }] : []),
  ];
  const { query: listQuery, result } = useInfiniteList<PrefillQueueItem>({
    resource: RESOURCE,
    pagination: { currentPage: 1, pageSize: PAGE_SIZE },
    filters,
    queryOptions: {
      getNextPageParam: (lastPage, allPages) =>
        allPages.reduce((count, page) => count + page.data.length, 0) < (lastPage.total ?? 0) ? allPages.length + 1 : undefined,
    },
  });
  const pages = result.data?.pages ?? [];
  const rows = pages.flatMap((page) => page.data);
  const total = pages[0]?.total ?? 0;
  const { fetchNextPage, isFetchingNextPage } = listQuery;
  const hasNextPage = !!result.hasNextPage;

  const scrollRef = useRef<HTMLDivElement>(null);
  const sentinelRef = useRef<HTMLTableRowElement>(null);
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasNextPage || isFetchingNextPage || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver((entries) => entries.some((e) => e.isIntersecting) && fetchNextPage(), {
      root: scrollRef.current,
      rootMargin: "200px",
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, rows.length, fetchNextPage]);

  async function remove(id: number) {
    setBusy(true);
    setError(undefined);
    try {
      // Every admin write needs a JSON Content-Type (the backend refuses others).
      await httpClient.delete(`${apiUrl}/${RESOURCE}/${id}`, { data: {}, headers: { "Content-Type": "application/json" } });
      // The rows and the Pending count in the Filter menu read the same resource.
      await invalidate({ resource: RESOURCE, invalidates: ["all"] });
      setConfirming(undefined);
    } catch (e) {
      setError((e as Error).message || "Could not remove it.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto">
        <table aria-label="Pending" className="w-full border-collapse text-sm">
          <thead className="sticky top-0 z-10 bg-muted">
            <tr>
              {[kind === "word" ? "Word" : "Sentence", "Seen", "From", "Queued", "Status", ""].map((header, i) => (
                <th key={i} scope="col" className="border-b border-border px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  {header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {listQuery.isLoading && (
              <tr>
                <td colSpan={6} className="p-4 text-muted-foreground">Loading...</td>
              </tr>
            )}
            {listQuery.isError && (
              <tr>
                <td colSpan={6} className="p-4 text-destructive">Could not load the queue.</td>
              </tr>
            )}
            {!listQuery.isLoading && !listQuery.isError && rows.length === 0 && (
              <tr>
                <td colSpan={6} className="p-4 text-muted-foreground">Nothing is waiting for the night job.</td>
              </tr>
            )}
            {rows.map((row) => (
              <tr key={row.id} className="border-b border-border/60">
                <td className="px-4 py-2.5 font-semibold">{row.text}</td>
                <td className="px-4 py-2.5 tabular-nums" title="How many sentences had this word">
                  {row.seenCount}
                </td>
                <td className="px-4 py-2.5 text-muted-foreground">{SOURCES[row.source] ?? row.source}</td>
                <td className="px-4 py-2.5 text-muted-foreground">{row.createdAt.slice(0, 10)}</td>
                <td className="px-4 py-2.5">
                  <Badge variant="default" dot title="Answered by the night job, then it moves to the list">
                    Pending
                  </Badge>
                </td>
                <td className="px-4 py-2.5 text-right">
                  {confirming === row.id ? (
                    <span role="alertdialog" aria-label={`Confirm remove ${row.text}`} className="inline-flex items-center gap-2">
                      <span className="text-xs text-muted-foreground">Remove from the queue?</span>
                      <Button size="icon" variant="destructive" aria-label="Confirm remove" title="Remove" disabled={busy} onClick={() => remove(row.id)}>
                        <Check className="h-4 w-4" />
                      </Button>
                      <Button size="icon" variant="outline" aria-label="Cancel remove" title="Cancel" onClick={() => setConfirming(undefined)}>
                        <X className="h-4 w-4" />
                      </Button>
                    </span>
                  ) : (
                    <Button
                      size="icon"
                      variant="ghost"
                      aria-label={`Remove ${row.text} from the queue`}
                      title="Remove from the queue"
                      onClick={() => setConfirming(row.id)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </td>
              </tr>
            ))}
            {hasNextPage && (
              <tr ref={sentinelRef} aria-hidden={!isFetchingNextPage}>
                <td colSpan={6} className="p-3 text-center">
                  {isFetchingNextPage && <Loader2 role="status" aria-label="Loading more" className="mx-auto h-4 w-4 animate-spin text-muted-foreground" />}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {error && (
        <p role="alert" className="border-t border-border px-4 py-2 text-sm text-destructive">
          {error}
        </p>
      )}
      <div className="border-t border-border p-3 text-sm text-muted-foreground">
        {rows.length.toLocaleString("en-US")} of {total.toLocaleString("en-US")} waiting for the night job
      </div>
    </>
  );
}
