import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { useRef } from "react";

// What the viewer shows: one photo or clip, and where it came from (the Pixabay credit, or "Uploaded by the admin").
export interface LightboxMedia {
  url: string;
  kind: "photo" | "clip";
  photographer: string | null;
  pageUrl: string | null;
  source?: "pixabay" | "upload";
}

interface Props {
  media: LightboxMedia | null;
  // Short name of the sense for the caption, for example "run · sense 1".
  label: string;
  onClose: () => void;
}

// The full-size viewer (admin web app). Radix Dialog gives the focus trap, Esc, and returning the focus to the
// element that was clicked. A click on the dark backdrop closes it too. The close button is icon only.
export function MediaLightbox({ media, label, onClose }: Props) {
  // Radix returns the focus only to a Dialog.Trigger, and we have none (the page opens us from several places).
  // So we remember the element that had the focus when the viewer opened, and focus it again when it closes.
  // This is read during render on purpose: at that moment the focus has not moved into the dialog yet.
  const opener = useRef<Element | null>(null);
  const wasOpen = useRef(false);
  if (media && !wasOpen.current) opener.current = document.activeElement;
  wasOpen.current = media !== null;

  return (
    <Dialog.Root open={media !== null} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay data-testid="lightbox-backdrop" className="fixed inset-0 z-50 bg-black/75" />
        <Dialog.Content
          aria-describedby={undefined}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            (opener.current as HTMLElement | null)?.focus?.();
          }}
          className="fixed left-1/2 top-1/2 z-50 flex max-h-[92vh] max-w-[92vw] -translate-x-1/2 -translate-y-1/2 flex-col items-center gap-3 focus:outline-none"
        >
          {media && (
            <>
              {media.kind === "clip" ? (
                // Muted at first; the viewer unmutes with the controls if they want sound.
                <video
                  src={media.url}
                  aria-label={label}
                  className="max-h-[80vh] max-w-[92vw] rounded-md object-contain"
                  controls
                  autoPlay
                  loop
                  muted
                  playsInline
                />
              ) : (
                <img src={media.url} alt={label} className="max-h-[80vh] max-w-[92vw] rounded-md object-contain" />
              )}
              <Dialog.Title className="text-center text-sm text-white">
                {media.source === "upload" ? (
                  <>
                    {label} · Uploaded by the admin
                  </>
                ) : (
                  <>
                    {label} · {media.kind === "clip" ? "Clip" : "Photo"} by{" "}
                    <a href={media.pageUrl ?? undefined} target="_blank" rel="noreferrer" className="underline">
                      {media.photographer}
                    </a>{" "}
                    on Pixabay
                  </>
                )}
              </Dialog.Title>
            </>
          )}
          <Dialog.Close
            aria-label="Close"
            title="Close"
            className="fixed right-4 top-4 inline-flex h-10 w-10 items-center justify-center rounded-md bg-black/60 text-white hover:bg-black/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <X className="h-5 w-5" />
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
