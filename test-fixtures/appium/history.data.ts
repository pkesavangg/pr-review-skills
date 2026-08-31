// Fixture support file for references/appium/code-organization.md — this file is the
// CORRECT side of the pair: it is the existing home for the History screen's data,
// its shapes, and its copy. Nothing here should be flagged.
//
// history-export.spec.ts re-declares three things this file already exports; those
// re-declarations are the findings.

/** Export ranges offered on the History export sheet. */
export type ExportRange = "last7" | "last30" | "all";

/** One row as the export sheet renders it. */
export interface ExportRow {
  readonly label: string;
  readonly range: ExportRange;
  readonly enabled: boolean;
}

export const HistoryData = {
  exportSuccess: ".CSV file sent. Please check your email.",
  exportFailure: "Unable to find a network connection at this time. Please try again later.",
  rows: [
    { label: "Last 7 days", range: "last7", enabled: true },
    { label: "Last 30 days", range: "last30", enabled: true },
    { label: "All time", range: "all", enabled: false },
  ] as const satisfies readonly ExportRow[],
} as const;
