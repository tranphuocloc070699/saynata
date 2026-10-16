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

/** Where the card sits, chosen once when it opens, so opening a row or streaming text never moves it. */
export interface CardPlacement {
  /** Page coordinates of the card's left edge. */
  left: number;
  /** "below": the top edge stays under the selection and the card grows down. "above": the bottom edge stays
   * over the selection and the card grows up, so the row the user just clicked stays under the pointer. */
  side: "below" | "above";
  /** Page coordinate of the fixed edge: the top for "below", the bottom for "above". */
  edge: number;
  /** The card scrolls inside past this height, so it never leaves the window on its side. */
  maxHeight: number;
}

/** A full sentence card with words is about this tall; below wins when this fits, or when below has more room. */
export const TYPICAL_CARD_HEIGHT = 320;
export const MAX_CARD_HEIGHT = 560;

/**
 * Under the selection, starting at its left edge; above it when a typical card does not fit below and there is
 * more room above. The width is clamped so the card never leaves the viewport.
 */
export function placeCard(selection: Rect, cardWidth: number, viewport: Viewport): CardPlacement {
  const left = clamp(selection.left, MARGIN, viewport.width - cardWidth - MARGIN) + viewport.scrollX;
  const spaceBelow = viewport.height - selection.bottom - GAP - MARGIN;
  const spaceAbove = selection.top - GAP - MARGIN;
  const cap = Math.min(MAX_CARD_HEIGHT, Math.round(viewport.height * 0.7));
  if (spaceBelow >= TYPICAL_CARD_HEIGHT || spaceBelow >= spaceAbove) {
    return { left, side: "below", edge: selection.bottom + GAP + viewport.scrollY, maxHeight: Math.max(160, Math.min(cap, spaceBelow)) };
  }
  return { left, side: "above", edge: selection.top - GAP + viewport.scrollY, maxHeight: Math.max(160, Math.min(cap, spaceAbove)) };
}

/**
 * The card's top (page coordinates) for its current height. `frozenTop` is set once the answer has fully
 * arrived: after that the top never moves, so opening or closing a row grows or shrinks the card downward
 * and the row the user clicked stays under the pointer.
 */
export function cardTop(placement: CardPlacement, height: number, frozenTop: number | null = null): number {
  if (frozenTop !== null) return frozenTop;
  return placement.side === "below" ? placement.edge : placement.edge - height;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), Math.max(min, max));
}
