/** Sense pictures and clips (`imageUrl`), the same checks as the extension (extension/src/content/media.ts). */

export const MEDIA_ORIGIN = "https://api.saynata.com";
/** The old address is an alias for now, so media links the server still writes with it are trusted too (TASK-104). */
const LEGACY_MEDIA_ORIGIN = "https://api.loffy.me";

/** Only our own server's media: an imageUrl is never followed to another site. */
export function isAllowedMedia(url: string, origin: string = MEDIA_ORIGIN): boolean {
  try {
    const parsed = new URL(url);
    return [new URL(origin).origin, LEGACY_MEDIA_ORIGIN].includes(parsed.origin) && parsed.pathname.startsWith("/media/");
  } catch {
    return false;
  }
}

export function isClip(url: string): boolean {
  return /\.(mp4|webm|mov)$/i.test(new URL(url).pathname);
}
