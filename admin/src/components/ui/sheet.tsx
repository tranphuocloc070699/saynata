import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ReactNode } from "react";

interface Props {
  open: boolean;
  onClose: () => void;
  // Read by screen readers as the name of the drawer.
  title: string;
  children: ReactNode;
}

// shadcn/ui Sheet: a drawer from the right edge (Radix Dialog gives the focus trap and Esc).
// The close button is icon only.
export function Sheet({ open, onClose, title, children }: Props) {
  return (
    <Dialog.Root open={open} onOpenChange={(next) => !next && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
        <Dialog.Content
          aria-describedby={undefined}
          className="fixed inset-y-0 right-0 z-40 flex w-full max-w-3xl flex-col border-l border-border bg-card shadow-xl focus:outline-none"
        >
          <Dialog.Title className="sr-only">{title}</Dialog.Title>
          <div className="min-h-0 flex-1 overflow-y-auto p-6 pt-14">{children}</div>
          <Dialog.Close
            aria-label="Close"
            title="Close"
            className="absolute right-3 top-3 inline-flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <X className="h-4 w-4" />
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
