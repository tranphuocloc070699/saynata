/** Sense pictures and clips (`imageUrl`), the same checks as the extension (extension/src/content/media.ts). */

export const MEDIA_ORIGIN = "https://api.loffy.me";

/** Only our own server's media: an imageUrl is never followed to another site. */
export function isAllowedMedia(url: string, origin: string = MEDIA_ORIGIN): boolean {
  try {
    const parsed = new URL(url);
    return parsed.origin === new URL(origin).origin && parsed.pathname.startsWith("/media/");
  } catch {
    return false;
  }
}

export function isClip(url: string): boolean {
  return /\.(mp4|webm|mov)$/i.test(new URL(url).pathname);
}
