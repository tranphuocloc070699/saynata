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
 * Where the card sits (the developer's rule, 2026-10-17): **beside the text**, its vertical centre level with
 * the text's centre; to the right first, then the left; when neither side has room, under the text centred on
 * it, then over it. The card never covers the selected text.
 * The side and the left edge are chosen once, when the card opens. The top follows the card's height while the
 * answer arrives (so the centre stays on the text), then it is frozen; later growth (a row, a word, a picture)
 * goes downward and scrolls inside the card at the bottom of the window. All values are page coordinates.
 */
export interface CardPlacement {
  side: "right" | "left" | "below" | "above";
  left: number;
  /** right/left: the text's vertical centre; below: the top edge; above: the bottom edge. */
  anchorY: number;
  /** The card's top may not go above this (the window's top + margin). */
  minTop: number;
  /** The card's bottom may not go below this (the window's bottom - margin). */
  maxBottom: number;
}

export const SIDE_GAP = 10;

/**
 * The rect the card is placed against: the whole selection, or its last line when the selection is very wide
 * (a multi-line selection usually fills the column, so no side would have room).
 */
export function anchorRect(selection: Rect, lastLine: Rect, viewport: Viewport): Rect {
  return selection.right - selection.left > viewport.width / 2 ? lastLine : selection;
}

export function placeCard(selection: Rect, lastLine: Rect, card: { width: number; height: number }, viewport: Viewport): CardPlacement {
  const anchor = anchorRect(selection, lastLine, viewport);
  const minTop = MARGIN + viewport.scrollY;
  const maxBottom = viewport.height - MARGIN + viewport.scrollY;
  const centreY = (anchor.top + anchor.bottom) / 2 + viewport.scrollY;

  if (anchor.right + SIDE_GAP + card.width + MARGIN <= viewport.width) {
    return { side: "right", left: anchor.right + SIDE_GAP + viewport.scrollX, anchorY: centreY, minTop, maxBottom };
  }
  if (anchor.left - SIDE_GAP - card.width >= MARGIN) {
    return { side: "left", left: anchor.left - SIDE_GAP - card.width + viewport.scrollX, anchorY: centreY, minTop, maxBottom };
  }
  // Neither side: under (or over) the whole selection, so no line of it is covered, centred on the anchor.
  const centreX = (anchor.left + anchor.right) / 2;
  const left = clamp(centreX - card.width / 2, MARGIN, viewport.width - card.width - MARGIN) + viewport.scrollX;
  const spaceBelow = viewport.height - selection.bottom - GAP - MARGIN;
  const spaceAbove = selection.top - GAP - MARGIN;
  if (card.height <= spaceBelow || spaceBelow >= spaceAbove) {
    return { side: "below", left, anchorY: selection.bottom + GAP + viewport.scrollY, minTop, maxBottom };
  }
  return { side: "above", left, anchorY: selection.top - GAP + viewport.scrollY, minTop, maxBottom };
}

/** The card's top for its current height, kept inside the window. `frozenTop` wins once the answer is complete. */
export function cardTop(placement: CardPlacement, height: number, frozenTop: number | null = null): number {
  if (frozenTop !== null) return frozenTop;
  switch (placement.side) {
    case "right":
    case "left":
      return clamp(placement.anchorY - height / 2, placement.minTop, placement.maxBottom - height);
    case "below":
      return placement.anchorY;
    case "above":
      return Math.max(placement.minTop, placement.anchorY - height);
  }
}

/** How tall the card may be from this top: it scrolls inside instead of moving or leaving the window. */
export function cardMaxHeight(placement: CardPlacement, top: number): number {
  const bottom = placement.side === "above" ? placement.anchorY : placement.maxBottom;
  return Math.max(160, Math.min(MAX_CARD_HEIGHT, bottom - top));
}

export const MAX_CARD_HEIGHT = 560;

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), Math.max(min, max));
}
