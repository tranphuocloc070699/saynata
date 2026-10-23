/**
 * The web IconButton, like the iOS one: icon only, an aria-label, and always a 44 px tap target.
 * `prominent` is the one accent-filled main action; `compact` draws a 32 px circle inside the same target;
 * `plain` draws only the icon.
 */
import type { ButtonHTMLAttributes, ReactNode } from "react";

type Props = Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> & {
  label: string;
  icon: ReactNode;
  variant?: "default" | "prominent" | "plain";
  compact?: boolean;
  active?: boolean;
};

export function IconButton({ label, icon, variant = "default", compact, active, className = "", ...rest }: Props) {
  const circle = compact ? "size-8" : "size-11";
  const look =
    variant === "prominent"
      ? "bg-accent text-on-accent shadow-sm disabled:opacity-40"
      : variant === "plain"
        ? active
          ? "text-accent"
          : "text-muted hover:text-ink"
        : `border border-hairline bg-surface shadow-[0_1px_2px_rgb(0_0_0/0.05)] ${active ? "text-accent" : "text-ink-2 hover:text-ink"}`;
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={`group grid size-11 shrink-0 cursor-pointer place-items-center rounded-full disabled:cursor-default ${className}`}
      {...rest}
    >
      <span
        className={`grid ${circle} place-items-center rounded-full transition-transform duration-150 group-active:scale-95 motion-reduce:transition-none ${look}`}
      >
        <span className={compact ? "size-4" : "size-[18px]"}>{icon}</span>
      </span>
    </button>
  );
}
