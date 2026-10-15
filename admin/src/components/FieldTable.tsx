import type { ReactNode } from "react";

export interface FieldRow {
  label: string;
  value: ReactNode;
  // A heading row spans the table, for example "Sense 1".
  heading?: boolean;
}

// The selected entry as a plain table of fields: label on the left, value on the right.
export function FieldTable({ rows }: { rows: FieldRow[] }) {
  return (
    <table className="w-full border-collapse text-sm">
      <tbody>
        {rows.map((row, index) =>
          row.heading ? (
            <tr key={index}>
              <th colSpan={2} className="bg-muted px-3 py-2 text-left text-xs font-semibold uppercase tracking-wide">
                {row.label}
              </th>
            </tr>
          ) : (
            <tr key={index} className="border-b border-border align-top">
              <th scope="row" className="w-40 px-3 py-2 text-left font-medium text-muted-foreground">
                {row.label}
              </th>
              <td className="px-3 py-2">{row.value}</td>
            </tr>
          ),
        )}
      </tbody>
    </table>
  );
}
