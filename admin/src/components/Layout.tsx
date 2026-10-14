import { useGetIdentity, useLogout } from "@refinedev/core";
import { BookOpen, Image, LogOut, MessageSquareText } from "lucide-react";
import { NavLink, Outlet } from "react-router";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const links = [
  { to: "/words", label: "Words", icon: BookOpen },
  { to: "/sentences", label: "Sentences", icon: MessageSquareText },
  { to: "/media", label: "Media", icon: Image },
];

// Left sidebar with the three sections; the page goes on the right.
export function Layout() {
  const { data: user } = useGetIdentity<{ name: string; email: string }>();
  const { mutate: logout } = useLogout();

  return (
    <div className="grid h-screen grid-cols-[200px_1fr]">
      <aside className="flex flex-col border-r border-border bg-card p-3">
        <p className="px-3 py-2 text-lg font-semibold">Saynata</p>
        <nav className="mt-2 flex flex-1 flex-col gap-1" aria-label="Sections">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium hover:bg-muted",
                  isActive && "bg-accent text-accent-foreground",
                )
              }
            >
              <Icon className="h-4 w-4" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="flex items-center justify-between gap-2 border-t border-border px-1 pt-3">
          <span className="truncate text-xs text-muted-foreground">{user?.name ?? user?.email}</span>
          <Button variant="ghost" size="icon" aria-label="Sign out" title="Sign out" onClick={() => logout()}>
            <LogOut className="h-4 w-4" />
          </Button>
        </div>
      </aside>
      <main className="min-h-0 overflow-hidden">
        <Outlet />
      </main>
    </div>
  );
}
