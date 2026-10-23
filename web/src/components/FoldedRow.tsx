"use client";
/** A row that opens and closes, closed by default (design system rule 9). The title is text, not a button label. */
import { useId, useState, type ReactNode } from "react";
import { Chevron } from "./icons";

export function FoldedRow({ title, children }: { title: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <div className="border-t border-hairline">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-11 w-full cursor-pointer items-center justify-between gap-3 py-2 text-left text-sm font-semibold text-ink-2"
      >
        {title}
        <Chevron
          className={`size-4 text-muted transition-transform duration-200 motion-reduce:transition-none ${open ? "rotate-180" : ""}`}
        />
      </button>
      <div id={id} className="fold-body" data-open={open} inert={!open}>
        <div>
          <div className="pb-3">{children}</div>
        </div>
      </div>
    </div>
  );
}
