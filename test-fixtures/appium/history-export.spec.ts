// Fixture for references/appium/code-organization.md. Every block below is a
// deliberate trigger — do not "fix" this file; it exists to be flagged.
// Its correct counterpart is history.data.ts, which already exports everything
// this spec re-declares.
//
// Expected findings:
//   P2  type/interface declared inside a *.spec.ts (ExportOutcome, Expected)
//   P2  a module's types split across a second home — ExportRange re-spelled
//       inline in a signature when history.data.ts already exports it
//   P2  new duplication — the same 7-line export journey copy-pasted across two its
//   P2  repeated literal 3+ times (the success-toast copy) when HistoryData exports it
//   P2  spec-local helper with a second call site → belongs in test/helpers/
//   P2  contradicts a documented standard — driver.isAndroid outside PlatformHelper
//       (ADR-0002, lint level `error`) and .catch(() => false) feeding an assertion
//       (TEST-STANDARDS.md: no silent failures)
//   Nit comments that restate the next line or narrate the change

import "mocha";
import HistoryPage from "../pageobjects/history.page";
import { HistoryData } from "../data/history.data";

// P2 — a shape declared in a spec: the next spec that needs it will re-declare it.
//       Belongs beside the fixtures it types, in history.data.ts.
interface Expected {
  readonly label: string;
  readonly toast: string;
}

// P2 — same defect, alias form.
type ExportOutcome = "sent" | "failed";

// P2 — spec-local helper, and export-history.spec.ts needs the identical body.
//       One call site is local; two is shared → test/helpers/.
async function seedThreeWeighIns(email: string): Promise<void> {
  await HistoryPage.openManualEntry();
  await HistoryPage.addWeighIn(email, "170");
  await HistoryPage.addWeighIn(email, "171");
  await HistoryPage.addWeighIn(email, "172");
}

// P2 — the union already exists as ExportRange in history.data.ts; re-spelling it
//       here means adding a fourth range requires finding both copies.
async function exportAs(range: "last7" | "last30" | "all"): Promise<ExportOutcome> {
  await HistoryPage.selectRange(range);
  await HistoryPage.tapExport();
  return "sent";
}

describe("History — export", () => {
  it("MA-T4001 — exporting the last 30 days emails a CSV", async () => {
    await seedThreeWeighIns("a@example.com");

    // Nit — restates the next line.
    // Open the export sheet
    await HistoryPage.openExportSheet();
    await HistoryPage.selectRange("last30");
    await HistoryPage.tapExport();
    const toast = await HistoryPage.readExportToast();
    // P2 — literal copy, third occurrence in this file; HistoryData.exportSuccess exists.
    expect(toast).toContain(".CSV file sent. Please check your email.");

    // P2 — silent failure feeding an assertion (TEST-STANDARDS.md).
    //       Use ElementHelper.isDisplayedNow / isDisplayedSafe.
    const shown = await HistoryPage.exportRow.isDisplayed().catch(() => false);
    expect(shown).toBe(true);
  });

  it("MA-T4002 — exporting all time emails a CSV", async () => {
    await seedThreeWeighIns("b@example.com");

    // P2 — the same seven-line journey again. Extract one page-object action or
    //       flow helper and call it from both tests.
    await HistoryPage.openExportSheet();
    await HistoryPage.selectRange("all");
    await HistoryPage.tapExport();
    const toast = await HistoryPage.readExportToast();
    expect(toast).toContain(".CSV file sent. Please check your email.");

    // Nit — narrates the diff; belongs in the commit message.
    // Changed this from 5000 to 8000 because it was flaky
    await HistoryPage.exportRow.waitForDisplayed({ timeout: 8000 });

    // P2 — driver.isAndroid outside PlatformHelper (ADR-0002, lint `error`).
    //       Use platformLocator(android, ios).
    const label = driver.isAndroid ? "Add a Baby" : "Add Baby";
    expect(label).toBeTruthy();

    // Nit — commented-out leftover with no ticket.
    // const oldSelector = '~export-btn';   // keeping this just in case
  });

  it("MA-T4003 — a failed export reports the network error", async () => {
    const expected: Expected = { label: "All time", toast: HistoryData.exportFailure };
    const outcome = await exportAs("all");
    expect(outcome).toBe("failed");
    expect(expected.toast).toBeTruthy();
  });
});
