/**
 * The Mochi cat on the composer card (paid CatMegaBundle sprites, doc-23). The PNGs are never in git or in the
 * build: scripts/upload-web-cats.sh copies them to the server and Caddy serves them at /cats/ (doc-11 section 14).
 * Every sheet is one row of 32x32 frames, and every frame's drawing ends on the frame's bottom row, so one box
 * anchored to the card edge fits all of them.
 */

export interface CatSheet {
  /** File under /cats/. */
  file: string;
  frames: number;
  fps: number;
  /** Which of the five visit choices it is: Dance, Sleeping, Excited, Sick 1 or Box. */
  kind: "dance" | "sleep" | "excited" | "sick" | "box";
}

export const CAT_SHEETS = {
  dance: { file: "Dance.png", frames: 4, fps: 6, kind: "dance" },
  sleep: { file: "Sleep.png", frames: 4, fps: 3, kind: "sleep" },
  excited: { file: "Excited.png", frames: 12, fps: 8, kind: "excited" },
  sick: { file: "catsick1.png", frames: 5, fps: 4, kind: "sick" },
  box1: { file: "Box1.png", frames: 4, fps: 6, kind: "box" },
  box2: { file: "Box2.png", frames: 12, fps: 6, kind: "box" },
  box3: { file: "Box3.png", frames: 4, fps: 6, kind: "box" },
} as const satisfies Record<string, CatSheet>;

export const CAT_BASE_URL = "/cats/";
/** One frame is 32 px; 3x keeps the pixels square and crisp. */
export const CAT_FRAME_PX = 32;
export const CAT_SCALE = 3;

const KINDS = [CAT_SHEETS.dance, CAT_SHEETS.sleep, CAT_SHEETS.excited, CAT_SHEETS.sick, "box"] as const;
const BOXES = [CAT_SHEETS.box1, CAT_SHEETS.box2, CAT_SHEETS.box3] as const;

/** One of the five with equal chance; Box then picks one of its three sheets. `random` returns [0, 1). */
export function pickCat(random: () => number = Math.random): CatSheet {
  const kind = KINDS[Math.min(KINDS.length - 1, Math.floor(random() * KINDS.length))];
  if (kind !== "box") return kind;
  return BOXES[Math.min(BOXES.length - 1, Math.floor(random() * BOXES.length))];
}

let visit: CatSheet | null = null;

/**
 * The cat for this page visit. It is kept in module memory, so typing, focus, re-renders, client navigation and
 * tab hide/show all get the same cat; only a reload (a new page load runs this module again) picks a new one.
 */
export function visitCat(random: () => number = Math.random): CatSheet {
  visit ??= pickCat(random);
  return visit;
}

/** For tests only: forget this visit's cat. */
export function resetVisitCat(): void {
  visit = null;
}

export type SheetLoader = (url: string) => Promise<boolean>;

/** Loads the picture in the browser; false when it is missing (local dev, or before the upload). */
export const loadImage: SheetLoader = (url) =>
  new Promise((resolve) => {
    const image = new Image();
    image.onload = () => resolve(true);
    image.onerror = () => resolve(false);
    image.src = url;
  });

const checks = new Map<string, Promise<boolean>>();

/** Whether the sheet can be shown, asked once per address. */
export function sheetAvailable(sheet: CatSheet, load: SheetLoader = loadImage): Promise<boolean> {
  const url = CAT_BASE_URL + sheet.file;
  let check = checks.get(url);
  if (!check) {
    check = load(url).catch(() => false);
    checks.set(url, check);
  }
  return check;
}

/** For tests only. */
export function resetSheetChecks(): void {
  checks.clear();
}

/** The CSS custom properties that drive the steps() animation in globals.css (.mochi). */
export function sheetStyle(sheet: CatSheet, scale: number = CAT_SCALE): Record<string, string> {
  const size = CAT_FRAME_PX * scale;
  return {
    "--mochi-size": `${size}px`,
    "--mochi-strip": `${size * sheet.frames}px`,
    "--mochi-frames": String(sheet.frames),
    "--mochi-duration": `${(sheet.frames / sheet.fps).toFixed(3)}s`,
    backgroundImage: `url("${CAT_BASE_URL}${sheet.file}")`,
  };
}
