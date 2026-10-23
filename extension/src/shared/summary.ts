/**
 * The small Markdown of a sentence summary (prompt v9, TASK-89.3, doc-4): plain lines, bullet lines that start
 * with "- ", and **bold** key words. Nothing else is read. The card builds DOM nodes from it (never innerHTML),
 * so text from the backend can never become HTML.
 */

export interface SummaryPiece {
  text: string;
  bold: boolean;
}

export interface SummaryLine {
  bullet: boolean;
  pieces: SummaryPiece[];
}

/**
 * Splits `markdown` into lines. Text and "**" alternate, so every odd piece is bold. While the answer streams,
 * a "**" that is not closed yet makes the rest of the line bold, and the marker never shows. Empty lines go.
 */
export function parseSummary(markdown: string): SummaryLine[] {
  return markdown
    .replace(/\r\n?/g, "\n")
    .split("\n")
    .filter((line) => line.trim() !== "")
    .map((line) => {
      const bullet = line.startsWith("- ");
      const body = bullet ? line.slice(2) : line;
      const pieces = body
        .split("**")
        .map((text, index) => ({ text, bold: index % 2 === 1 }))
        .filter((piece) => piece.text !== "");
      return { bullet, pieces };
    });
}
