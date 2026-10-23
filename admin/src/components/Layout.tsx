import { useGetIdentity, useLogout } from "@refinedev/core";
import { BookOpen, Image, LogOut, MessageSquareText, Search } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PageHeader } from "@/components/PageHeader";
import { cn } from "@/lib/utils";

const links = [
  { to: "/words", label: "Words", icon: BookOpen },
  { to: "/sentences", label: "Sentences", icon: MessageSquareText },
  { to: "/media", label: "Media", icon: Image },
];

// The Saynata mark: a teal square with an S, next to the name.
export function Logo() {
  return (
    <span className="flex items-center gap-2 font-heading text-[17px] font-semibold">
      <span aria-hidden className="grid h-7 w-7 place-items-center rounded-lg bg-primary text-sm text-primary-foreground">
        S
      </span>
      saynata admin
    </span>
  );
}

// Top bar search: jumps to the Words page with the text as its search (/words?q=...).
function HeaderSearch() {
  const navigate = useNavigate();
  const [text, setText] = useState("");
  function submit(event: FormEvent) {
    event.preventDefault();
    const q = text.trim();
    if (q) navigate(`/words?q=${encodeURIComponent(q)}`);
  }
  return (
    <form role="search" onSubmit={submit} className="relative w-72">
      <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
      <Input
        type="search"
        aria-label="Search words"
        placeholder="Search words…"
        className="h-9 pl-9"
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
    </form>
  );
}

// The round avatar opens a small menu with who is signed in and the Sign out button.
function AccountMenu() {
  const { data: user } = useGetIdentity<{ name: string; email: string }>();
  const { mutate: logout } = useLogout();
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const name = user?.name ?? user?.email ?? "";

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

  return (
    <div ref={box} className="relative">
      <button
        type="button"
        aria-label="Account"
        title="Account"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="grid h-9 w-9 place-items-center rounded-full bg-accent text-sm font-semibold text-accent-foreground"
      >
        {name.slice(0, 1).toUpperCase() || "?"}
      </button>
      {open && (
        <div className="absolute right-0 top-11 z-30 flex w-64 items-center gap-3 rounded-xl border border-border bg-card p-3 shadow-lg">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{user?.name}</p>
            <p className="truncate text-xs text-muted-foreground">{user?.email}</p>
          </div>
          <Button variant="outline" size="icon" aria-label="Sign out" title="Sign out" onClick={() => logout()}>
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  );
}

// Isomorphic-style shell (TASK-99): white sidebar with a section label, a top bar with search and the account
// menu, then the page title with its breadcrumb and the page itself in a white card.
export function Layout() {
  const { pathname } = useLocation();
  const current = links.find((link) => pathname.startsWith(link.to));

  return (
    <div className="grid h-screen grid-cols-[240px_1fr] grid-rows-[64px_1fr]">
      <aside className="row-span-2 flex flex-col border-r border-border bg-card px-3 py-5">
        <div className="px-2 pb-4">
          <Logo />
        </div>
        <p className="mx-2 mb-2 mt-3 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">Content</p>
        <nav className="flex flex-col gap-1" aria-label="Sections">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  "relative flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium hover:bg-muted",
                  isActive &&
                    "bg-accent text-accent-foreground before:absolute before:-left-3 before:inset-y-1.5 before:w-[3px] before:rounded-r before:bg-primary",
                )
              }
            >
              <Icon className="h-4 w-4" />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <header className="flex items-center justify-between border-b border-border bg-card px-6">
        <HeaderSearch />
        <AccountMenu />
      </header>
      <main className="flex min-h-0 flex-col gap-4 overflow-hidden p-6">
        {current && <PageHeader title={current.label} crumbs={["Content", current.label]} />}
        <div className="min-h-0 flex-1 overflow-hidden rounded-xl border border-border bg-card">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
