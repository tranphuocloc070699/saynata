/**
 * Where the icon and the card go. Rects are viewport coordinates (getBoundingClientRect); the results are
 * page coordinates (the layer is absolute at the top of the document), so they scroll with the text.
 */

export interface Rect {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export interface Viewport {
  width: number;
  height: number;
  scrollX: number;
  scrollY: number;
}

export interface Point {
  left: number;
  top: number;
}

const GAP = 8;
const MARGIN = 8;
export const ICON_SIZE = 28;

/** Just below the end of the selection (its last line), like Google Translate, kept inside the viewport. */
export function iconPosition(lastLine: Rect, viewport: Viewport): Point {
  const left = clamp(lastLine.right - ICON_SIZE / 2, MARGIN, viewport.width - ICON_SIZE - MARGIN);
  const below = lastLine.bottom + 4;
  const top = below + ICON_SIZE + MARGIN > viewport.height ? lastLine.top - ICON_SIZE - 4 : below;
  return { left: left + viewport.scrollX, top: Math.max(MARGIN, top) + viewport.scrollY };
}

/**
 * Under the selection, starting at its left edge; above it when there is not enough room below and more
 * room above. The width is clamped so the card never leaves the viewport.
 */
export function cardPosition(selection: Rect, card: { width: number; height: number }, viewport: Viewport): Point {
  const left = clamp(selection.left, MARGIN, viewport.width - card.width - MARGIN);
  const spaceBelow = viewport.height - selection.bottom - GAP - MARGIN;
  const spaceAbove = selection.top - GAP - MARGIN;
  let top: number;
  if (card.height <= spaceBelow || spaceBelow >= spaceAbove) top = selection.bottom + GAP;
  else top = Math.max(MARGIN, selection.top - GAP - card.height);
  return { left: left + viewport.scrollX, top: top + viewport.scrollY };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), Math.max(min, max));
}
