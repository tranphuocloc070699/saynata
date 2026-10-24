import { useApiUrl, useCustom, useInfiniteList, useInvalidate, useOne, type CrudFilter } from "@refinedev/core";
import { Ban, Check, Loader2, Play, Search, Trash2, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { SignInProvider, UserDetail, UserRow, UsersSummary } from "@/api/types";
import type { Chip } from "@/components/EntryBrowser";
import { chipFilter, FilterMenu } from "@/components/FilterMenu";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Sheet } from "@/components/ui/sheet";
import { httpClient } from "@/providers/dataProvider";
import { cn } from "@/lib/utils";

const PAGE_SIZE = 50;

// Status: one at a time (a blocked admin cannot exist). Sign-in: one at a time.
const chips: Chip[] = [
  { id: "blocked", label: "Blocked", description: "No new Claude explanation; cached words still work.", field: "blocked", value: true, group: "Status" },
  { id: "admin", label: "Admin", description: "Can open this admin. Cannot be blocked or deleted here.", field: "admin", value: true, group: "Status" },
  { id: "email", label: "Email only", description: "Signs in with an emailed code.", field: "provider", value: "email", group: "Sign-in" },
  { id: "google", label: "Google", field: "provider", value: "google", group: "Sign-in" },
  { id: "apple", label: "Apple", field: "provider", value: "apple", group: "Sign-in" },
];

const PROVIDERS: Record<SignInProvider | "email", { mark: string; name: string }> = {
  email: { mark: "@", name: "Email code" },
  google: { mark: "G", name: "Google" },
  apple: { mark: "A", name: "Apple" },
};

const money = (usd: number) => `$${usd.toFixed(2)}`;
const count = (n: number) => n.toLocaleString("en-US");

// "2 h ago", "yesterday", or the date. The server saves last seen at most once an hour.
export function ago(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return "never";
  const minutes = Math.floor((now - new Date(iso).getTime()) / 60000);
  if (minutes < 60) return "this hour";
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)} h ago`;
  if (minutes < 48 * 60) return "yesterday";
  return iso.slice(0, 10);
}

function ProviderMarks({ providers }: { providers: SignInProvider[] }) {
  const list: (SignInProvider | "email")[] = providers.length ? providers : ["email"];
  return (
    <span className="flex gap-1">
      {list.map((p) => (
        <span
          key={p}
          title={PROVIDERS[p].name}
          aria-label={PROVIDERS[p].name}
          className="grid h-6 w-6 place-items-center rounded-md bg-muted text-[11px] font-semibold text-muted-foreground"
        >
          {PROVIDERS[p].mark}
        </span>
      ))}
    </span>
  );
}

// The only badges of a row: something that changes what the account can do.
function StatusBadge({ user }: { user: UserRow }) {
  if (user.blocked)
    return (
      <Badge variant="danger" dot>
        Blocked
      </Badge>
    );
  if (user.admin) return <Badge variant="outline">Admin</Badge>;
  return null;
}

// The four totals on top of the page (GET /users/summary).
function Summary({ summary: loaded }: { summary?: UsersSummary }) {
  // Refine gives an empty object while the request runs.
  const summary = typeof loaded?.accounts === "number" ? loaded : undefined;
  const since = summary?.usageSince ? `since ${summary.usageSince.slice(5, 10)}` : "no usage saved yet";
  const boxes = [
    ["Accounts", summary ? count(summary.accounts) : "…"],
    ["Active today", summary ? count(summary.activeToday) : "…"],
    ["New explanations today", summary ? count(summary.explanationsToday) : "…"],
    ["Cost today", summary ? money(summary.costTodayUsd) : "…"],
  ];
  return (
    <div className="space-y-1.5">
      <div role="group" aria-label="Totals" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {boxes.map(([label, value]) => (
          <div key={label} className="rounded-xl border border-border bg-card px-4 py-3">
            <p className="text-sm text-muted-foreground">{label}</p>
            <p className="mt-1 font-heading text-xl font-semibold tabular-nums">{value}</p>
          </div>
        ))}
      </div>
      <p className="text-xs text-muted-foreground">Usage per user is counted {since} (older calls were saved without a user).</p>
    </div>
  );
}

function UsageBars({ days }: { days: UserDetail["days"] }) {
  const most = Math.max(1, ...days.map((d) => d.calls));
  return (
    <div className="mt-3 flex h-16 items-end gap-1" role="img" aria-label="New explanations in the last 14 days">
      {days.map((day, i) => (
        <span
          key={day.date}
          title={`${day.date}: ${day.calls}`}
          className={cn("flex-1 rounded-t-sm", i === days.length - 1 ? "bg-primary" : "bg-accent")}
          style={{ height: `${Math.max(4, (day.calls / most) * 100)}%` }}
        />
      ))}
    </div>
  );
}

type Confirm = "block" | "delete" | undefined;

// The right drawer for one account: usage, sign-in methods, the account, and the Block / Delete buttons.
function UserPanel({ id, onDeleted, onChanged }: { id: number; onDeleted: () => void; onChanged: () => void }) {
  const apiUrl = useApiUrl();
  const invalidate = useInvalidate();
  const { result: detail, query } = useOne<UserDetail>({ resource: "users", id });
  const [confirm, setConfirm] = useState<Confirm>();
  const [reason, setReason] = useState("");
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  async function run(action: () => Promise<unknown>, afterwards?: () => void) {
    setBusy(true);
    setError(undefined);
    try {
      // Every admin write needs a JSON Content-Type (the backend refuses others).
      await action();
      setConfirm(undefined);
      // Close the drawer first after a delete: reading a deleted account again would only give a 404.
      afterwards?.();
      onChanged();
      await invalidate({ resource: "users", invalidates: ["all"] });
    } catch (e) {
      setError((e as Error).message || "Something went wrong.");
    } finally {
      setBusy(false);
    }
  }

  const json = { headers: { "Content-Type": "application/json" } };
  const block = () => run(() => httpClient.post(`${apiUrl}/users/${id}/block`, { reason }, json));
  const unblock = () => run(() => httpClient.post(`${apiUrl}/users/${id}/unblock`, {}, json));
  const remove = () => run(() => httpClient.delete(`${apiUrl}/users/${id}`, { data: {}, ...json }), onDeleted);

  if (query.isLoading || !detail) {
    return <p className="text-sm text-muted-foreground">{query.isError ? "Could not load this account." : "Loading..."}</p>;
  }
  const { user } = detail;
  const adminNote = "Admin accounts cannot be blocked or deleted here.";

  return (
    <section aria-label="Account" className="space-y-6">
      <div className="flex items-start justify-between gap-4 pr-10">
        <div className="min-w-0">
          <h2 className="truncate text-xl font-semibold">{user.email}</h2>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            User {user.id} · created {user.createdAt.slice(0, 10)} <StatusBadge user={user} />
          </p>
        </div>
        <div className="flex shrink-0 gap-2">
          {user.blocked ? (
            <Button variant="outline" size="icon" aria-label="Unblock" title="Unblock" disabled={busy} onClick={unblock}>
              <Play className="h-4 w-4" />
            </Button>
          ) : (
            <Button
              variant="outline"
              size="icon"
              aria-label="Block"
              title={user.admin ? adminNote : "Block"}
              disabled={busy || user.admin}
              onClick={() => setConfirm("block")}
            >
              <Ban className="h-4 w-4" />
            </Button>
          )}
          <Button
            variant="outline"
            size="icon"
            aria-label="Delete account"
            title={user.admin ? adminNote : "Delete account"}
            className="text-destructive"
            disabled={busy || user.admin}
            onClick={() => setConfirm("delete")}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {confirm === "block" && (
        <div role="alertdialog" aria-label="Confirm block" className="space-y-3 rounded-xl border border-destructive/40 p-4 text-sm">
          <p className="font-semibold">Block {user.email}?</p>
          <p className="text-muted-foreground">
            They keep answers that are bundled or already cached, but get no new Claude explanation. Nothing is deleted. You can
            unblock any time.
          </p>
          <label className="block space-y-1.5">
            <span className="font-medium">Reason (only admins see it)</span>
            <Input value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} placeholder="For example: 900 calls in one day" />
          </label>
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="icon" aria-label="Cancel block" title="Cancel" onClick={() => setConfirm(undefined)}>
              <X className="h-4 w-4" />
            </Button>
            <Button variant="destructive" size="icon" aria-label="Confirm block" title="Block" disabled={busy} onClick={block}>
              <Check className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {confirm === "delete" && (
        <div role="alertdialog" aria-label="Confirm delete" className="space-y-3 rounded-xl border border-destructive/40 p-4 text-sm">
          <p className="font-semibold">Delete {user.email}?</p>
          <p className="text-muted-foreground">
            Same as "Delete account" in the app: the account, its sign-in methods, its signed-in devices and its Liked list go. Usage
            stays for the cost history, without the person. This cannot be undone.
          </p>
          <label className="block space-y-1.5">
            <span className="font-medium">Type the email to confirm</span>
            <Input value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
          </label>
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="icon" aria-label="Cancel delete" title="Cancel" onClick={() => setConfirm(undefined)}>
              <X className="h-4 w-4" />
            </Button>
            <Button
              variant="destructive"
              size="icon"
              aria-label="Confirm delete"
              title={typed.trim().toLowerCase() === user.email.toLowerCase() ? "Delete" : "Type the email first"}
              disabled={busy || typed.trim().toLowerCase() !== user.email.toLowerCase()}
              onClick={remove}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {user.blocked && (
        <p className="rounded-lg bg-danger-soft p-3 text-sm text-destructive">
          Blocked {detail.blockedAt?.slice(0, 16).replace("T", " ")}
          {detail.blockedReason ? ` · ${detail.blockedReason}` : ""}
        </p>
      )}

      <div>
        <h3 className="text-sm font-semibold">Usage (new explanations from Claude)</h3>
        <div className="mt-2 grid grid-cols-3 gap-3">
          {(
            [
              ["Today", detail.today],
              ["Last 7 days", detail.last7Days],
              ["Total", detail.total],
            ] as const
          ).map(([label, period]) => (
            <div key={label} className="rounded-lg bg-muted px-3 py-2">
              <p className="text-xs text-muted-foreground">{label}</p>
              <p className="font-heading text-lg font-semibold tabular-nums">{count(period.calls)}</p>
              <p className="text-xs tabular-nums text-muted-foreground">{money(period.costUsd)}</p>
            </div>
          ))}
        </div>
        <UsageBars days={detail.days} />
        <p className="mt-1 text-xs text-muted-foreground">
          Last 14 days · words {count(detail.wordsTotal)}, sentences {count(detail.sentencesTotal)} in total
        </p>
      </div>

      <div>
        <h3 className="text-sm font-semibold">Sign-in methods</h3>
        <dl className="mt-2 grid grid-cols-[130px_1fr] gap-x-3 gap-y-2 text-sm">
          <dt className="text-muted-foreground">Email code</dt>
          <dd>{user.email}</dd>
          {detail.identities.map((identity) => (
            <div key={identity.provider} className="contents">
              <dt className="text-muted-foreground">{PROVIDERS[identity.provider].name}</dt>
              <dd>
                {identity.email ?? "-"} · since {identity.since.slice(0, 10)}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      <div>
        <h3 className="text-sm font-semibold">Account</h3>
        <dl className="mt-2 grid grid-cols-[130px_1fr] gap-x-3 gap-y-2 text-sm">
          <dt className="text-muted-foreground">Last seen</dt>
          <dd>{ago(user.lastSeenAt)}</dd>
          <dt className="text-muted-foreground">Liked</dt>
          <dd>{count(detail.likedCount)} words and sentences</dd>
          <dt className="text-muted-foreground">Devices signed in</dt>
          <dd>{detail.devicesSignedIn}</dd>
          <dt className="text-muted-foreground">Status</dt>
          <dd>{user.blocked ? "Blocked" : "Active"}</dd>
        </dl>
      </div>
    </section>
  );
}

// The admin Users page (TASK-105, mockup 2026-10-24): totals, a table of accounts, and a drawer for one account.
export function UsersPage() {
  const apiUrl = useApiUrl();
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [active, setActive] = useState<string[]>([]);
  const [selectedId, setSelectedId] = useState<number>();

  useEffect(() => {
    const timer = setTimeout(() => setQuery(search.trim()), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const chosen = chips.filter((chip) => active.includes(chip.id));
  const filters: CrudFilter[] = [...(query ? [{ field: "q", operator: "contains" as const, value: query }] : []), ...chosen.map(chipFilter)];

  const { result: summaryResult, query: summaryQuery } = useCustom<UsersSummary>({ url: `${apiUrl}/users/summary`, method: "get" });

  const { query: listQuery, result } = useInfiniteList<UserRow>({
    resource: "users",
    pagination: { currentPage: 1, pageSize: PAGE_SIZE },
    filters,
    queryOptions: {
      getNextPageParam: (lastPage, allPages) =>
        allPages.reduce((n, page) => n + page.data.length, 0) < (lastPage.total ?? 0) ? allPages.length + 1 : undefined,
    },
  });
  const pages = result.data?.pages ?? [];
  const rows = pages.flatMap((page) => page.data);
  const total = pages[0]?.total ?? 0;
  const hasNextPage = !!result.hasNextPage;
  const { fetchNextPage, isFetchingNextPage } = listQuery;

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

  function toggle(chip: Chip) {
    // One choice per group: a new one replaces the other choice of its group.
    setActive((current) =>
      current.includes(chip.id)
        ? current.filter((id) => id !== chip.id)
        : [...current.filter((id) => chips.find((c) => c.id === id)?.group !== chip.group), chip.id],
    );
  }

  return (
    <div className="flex h-full flex-col gap-4 overflow-hidden p-4">
      <Summary summary={summaryResult?.data} />
      <section aria-label="List" className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-border">
        <div className="flex flex-wrap items-center gap-2 border-b border-border p-4">
          <div className="relative w-full max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Input type="search" aria-label="Search" placeholder="Search email" className="pl-9" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <div role="group" aria-label="Filters">
            <FilterMenu resource="users" chips={chips} active={active} onToggle={toggle} />
          </div>
        </div>
        <div ref={scrollRef} className="min-h-0 flex-1 overflow-auto">
          <table aria-label="Users" className="w-full border-collapse text-sm">
            <thead className="sticky top-0 z-10 bg-muted">
              <tr>
                {["Account", "Sign-in", "Created", "Last seen", "Today", "Total", "Cost", "Status"].map((header) => (
                  <th key={header} scope="col" className="border-b border-border px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                    {header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {listQuery.isLoading && (
                <tr>
                  <td colSpan={8} className="p-4 text-muted-foreground">Loading...</td>
                </tr>
              )}
              {listQuery.isError && (
                <tr>
                  <td colSpan={8} className="p-4 text-destructive">Could not load the accounts.</td>
                </tr>
              )}
              {!listQuery.isLoading && !listQuery.isError && rows.length === 0 && (
                <tr>
                  <td colSpan={8} className="p-4 text-muted-foreground">No account found.</td>
                </tr>
              )}
              {rows.map((user) => (
                <tr
                  key={user.id}
                  onClick={() => setSelectedId(user.id)}
                  className={cn("cursor-pointer border-b border-border/60 hover:bg-muted/60", user.id === selectedId && "bg-accent/60")}
                >
                  <td className="px-4 py-2.5">
                    <button
                      type="button"
                      aria-pressed={user.id === selectedId}
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedId(user.id);
                      }}
                      className="text-left font-semibold"
                    >
                      {user.email}
                    </button>
                  </td>
                  <td className="px-4 py-2.5">
                    <ProviderMarks providers={user.providers} />
                  </td>
                  <td className="px-4 py-2.5 text-muted-foreground">{user.createdAt.slice(0, 10)}</td>
                  <td className="px-4 py-2.5 text-muted-foreground">{ago(user.lastSeenAt)}</td>
                  <td className="px-4 py-2.5 tabular-nums">{count(user.explanationsToday)}</td>
                  <td className="px-4 py-2.5 tabular-nums">{count(user.explanationsTotal)}</td>
                  <td className="px-4 py-2.5 tabular-nums">{money(user.costTotalUsd)}</td>
                  <td className="px-4 py-2.5">
                    <StatusBadge user={user} />
                  </td>
                </tr>
              ))}
              {hasNextPage && (
                <tr ref={sentinelRef} aria-hidden={!isFetchingNextPage}>
                  <td colSpan={8} className="p-3 text-center">
                    {isFetchingNextPage && <Loader2 role="status" aria-label="Loading more" className="mx-auto h-4 w-4 animate-spin text-muted-foreground" />}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="border-t border-border p-3 text-sm text-muted-foreground">
          {count(rows.length)} of {count(total)}
        </div>
      </section>
      <Sheet open={selectedId !== undefined} onClose={() => setSelectedId(undefined)} title="Account details">
        {selectedId !== undefined && (
          <UserPanel key={selectedId} id={selectedId} onDeleted={() => setSelectedId(undefined)} onChanged={() => void summaryQuery.refetch()} />
        )}
      </Sheet>
    </div>
  );
}
