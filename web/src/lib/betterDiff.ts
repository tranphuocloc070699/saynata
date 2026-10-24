/**
 * Which words of the fixed text are new (TASK-110), so "Better way to say it" can show them in bold. Words are
 * compared without case, punctuation around them and curly apostrophes, like the backend's BetterCheck and the
 * app's BetterDiff: "I already do it." and "I've already done it." give "I've" and "done".
 */
export interface BetterPiece {
  /** A word with the spaces after it. Joined, the pieces are exactly the fixed text. */
  text: string;
  changed: boolean;
}

export function betterPieces(original: string, better: string): BetterPiece[] {
  const before = pieces(original).map(key).filter(Boolean);
  const after = pieces(better);
  const kept = keptIndices(before, after.map(key));
  return after.map((text, i) => ({ text, changed: key(text) !== "" && !kept.has(i) }));
}

function pieces(text: string): string[] {
  return text.match(/\S+\s*/g) ?? [];
}

function key(piece: string): string {
  return piece
    .toLowerCase()
    .replace(/[‘’]/g, "'")
    .replace(/^[^\p{L}\p{N}']+|[^\p{L}\p{N}']+$/gu, "");
}

/** The indices in b that are part of the longest list of words both have in the same order. */
function keptIndices(a: string[], b: string[]): Set<number> {
  const lengths = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0));
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      lengths[i][j] =
        b[j - 1] !== "" && a[i - 1] === b[j - 1]
          ? lengths[i - 1][j - 1] + 1
          : Math.max(lengths[i - 1][j], lengths[i][j - 1]);
    }
  }
  const kept = new Set<number>();
  let i = a.length;
  let j = b.length;
  while (i > 0 && j > 0) {
    if (b[j - 1] !== "" && a[i - 1] === b[j - 1]) {
      kept.add(j - 1);
      i--;
      j--;
    } else if (lengths[i - 1][j] >= lengths[i][j - 1]) {
      i--;
    } else {
      j--;
    }
  }
  return kept;
}
