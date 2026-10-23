import type { ReactNode } from "react";

interface Props {
  title: string;
  // From the section down to this page, for example ["Content", "Words"]. The last one is the current page.
  crumbs: string[];
  // Icon-only buttons on the right.
  actions?: ReactNode;
}

// Page title with a breadcrumb under it and the page actions on the right (Isomorphic look, TASK-99).
export function PageHeader({ title, crumbs, actions }: Props) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div>
        <h1 className="text-xl font-semibold">{title}</h1>
        <nav aria-label="Breadcrumb" className="mt-1 text-sm text-muted-foreground">
          {crumbs.map((crumb, i) => (
            <span key={crumb}>
              {i > 0 && <span className="mx-1.5">·</span>}
              <span className={i === crumbs.length - 1 ? "text-foreground" : undefined}>{crumb}</span>
            </span>
          ))}
        </nav>
      </div>
      {actions && <div className="flex gap-2">{actions}</div>}
    </div>
  );
}
