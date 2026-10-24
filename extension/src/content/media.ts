/**
 * Pictures and clips for word senses (`imageUrl`, served by the backend at /media/). Option A of the mockup:
 * 240x135 under each meaning, like the iOS SenseMediaView. A clip (.mp4) plays muted and loops.
 *
 * The <img> or <video> is tried first. Strict sites (a CSP `img-src` or `media-src` without our server)
 * block it, and then it fires `error`: the frame is swapped for an extension page in an iframe
 * (public/media.html). Extension pages are not bound by the page's CSP.
 * One node per address is kept while the card is open, so a re-render (Listen, a row) does not restart a clip.
 */

import { LEGACY_API_BASE } from "../shared/config";

export const MEDIA_WIDTH = 240;
export const MEDIA_HEIGHT = 135;

/**
 * Only our own server's media: an imageUrl is never followed to another site.
 * The old address counts as ours too, until the server writes media links with the new one (TASK-104).
 */
export function isAllowedMedia(url: string, apiBase: string): boolean {
  try {
    const parsed = new URL(url);
    const ours = [new URL(apiBase).origin, new URL(LEGACY_API_BASE).origin];
    return ours.includes(parsed.origin) && parsed.pathname.startsWith("/media/");
  } catch {
    return false;
  }
}

export function isClip(url: string): boolean {
  return /\.(mp4|webm|mov)$/i.test(new URL(url).pathname);
}

export class MediaNodes {
  private readonly nodes = new Map<string, HTMLElement>();

  constructor(
    private readonly apiBase: string,
    /** The extension page that shows one picture or clip, for sites that block ours. */
    private readonly fallbackUrl: (src: string) => string,
  ) {}

  /** The frame for this address (the same node every time), or null when the address is not ours. */
  get(url: string): HTMLElement | null {
    if (!isAllowedMedia(url, this.apiBase)) return null;
    const existing = this.nodes.get(url);
    if (existing) return existing;
    const frame = document.createElement("div");
    frame.className = "media";
    frame.append(this.element(url, frame));
    this.nodes.set(url, frame);
    return frame;
  }

  clear(): void {
    this.nodes.clear();
  }

  private element(url: string, frame: HTMLElement): HTMLElement {
    const loaded = () => frame.classList.add("loaded");
    const blocked = () => {
      const iframe = document.createElement("iframe");
      iframe.src = this.fallbackUrl(url);
      iframe.title = "Picture";
      iframe.setAttribute("aria-hidden", "true");
      iframe.addEventListener("load", loaded);
      frame.replaceChildren(iframe);
    };
    if (isClip(url)) {
      const video = document.createElement("video");
      video.muted = true; // the property, not only the attribute, lets it autoplay
      video.loop = true;
      video.autoplay = true;
      video.playsInline = true;
      video.setAttribute("muted", "");
      video.setAttribute("aria-hidden", "true");
      video.addEventListener("loadeddata", loaded, { once: true });
      video.addEventListener("error", blocked, { once: true });
      video.src = url;
      return video;
    }
    const image = document.createElement("img");
    image.alt = "";
    image.decoding = "async";
    image.addEventListener("load", loaded, { once: true });
    image.addEventListener("error", blocked, { once: true });
    image.src = url;
    return image;
  }
}
