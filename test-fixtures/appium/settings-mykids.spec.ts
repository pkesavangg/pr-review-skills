// Fixture for references/appium/test-naming-and-metadata.md and
// references/code-standards/typescript.md. Every block below is a deliberate
// trigger — do not "fix" this file; it exists to be flagged.
//
// Expected findings:
//   P1  invalid addSeverity value ("high" / "medium" / "low")
//   P1  test-case-ID drift (title vs addTestId vs addLabel("tms", …))
//   P1  new test with no case id / no metadata block
//   P2  four-call Allure boilerplate repeated per test → one testMeta() helper
//   P2  it title not "<ID> — <observable behaviour>" / separator drift
//   P2  describe title doesn't name the screen/section
//   P2  spec-local helper name doesn't say what it does
//   P2  stringly-typed value where a union should constrain it
//   P2  magic numbers, let-that-should-be-const

import "mocha";
import { addTestId, addFeature, addSeverity, addLabel } from "@wdio/allure-reporter";
import SettingsPage from "../pageobjects/settings.page";

// P2 — generic verb, no subject: what does it handle?
async function handle(): Promise<void> {
  let timeout = 4000; // P2 — never reassigned (const) + magic number
  await driver.waitUntil(async () => await SettingsPage.myKidsRow.isDisplayed(), {
    timeout,
    interval: 250, // P2 — magic number
  });
}

// P2 — boolean-returning helper with no is/has/can prefix
async function screenCheck(): Promise<boolean> {
  return SettingsPage.emptyState.isDisplayed();
}

// P2 — stringly-typed: unit is a closed vocabulary, but any string compiles
async function setUnits(unit: string): Promise<void> {
  await SettingsPage.openUnitType();
  if (unit === "imperial") {
    await SettingsPage.selectImperial();
  } else if (unit === "metric") {
    await SettingsPage.selectMetric();
  }
}

// P2 — describe title is the filename, not the screen under test
describe("settings-mykids.spec", () => {
  // P2 — four-call Allure boilerplate, repeated verbatim below every it()
  it("MA-T844 — Verify My Kids row is enabled after Baby Scale signup", async function () {
    addTestId("MA-T844");
    addFeature("Settings Phase 2 — My Kids");
    addSeverity("high"); // P1 — not an Allure severity; use Severity.CRITICAL
    addLabel("tms", "MA-T844");

    await handle();
    expect(await SettingsPage.myKidsRow.isEnabled()).toBe(true);
  });

  it("MA-T787: empty state", async function () {
    // P2 — ":" separator (file's dominant form is " — "), and "empty state" is
    //      not an observable behaviour
    addTestId("MA-T787");
    addFeature("Settings Phase 2 - My Kids"); // P2 — feature string retyped with a hyphen: forks the Allure tree
    addSeverity("medium"); // P1 — not an Allure severity; use Severity.NORMAL
    addLabel("tms", "MA-T789"); // P1 — ID drift: result lands on MA-T789, not MA-T787

    expect(await screenCheck()).toBe(true);
  });

  // P1 — no case id anywhere: invisible to Allure, writes no Zephyr result
  it("shows the Add Baby button", async function () {
    await SettingsPage.openMyKids();
    expect(await SettingsPage.addBabyButton.isDisplayed()).toBe(true);
  });

  it("[MA-T798] Save disabled by default", async function () {
    // P2 — third title format in one file ("[ID]"), breaking the prefix parser
    addTestId("MA-T798");
    addFeature("Settings Phase 2 — My Kids");
    addSeverity("low"); // P1 — not an Allure severity; use Severity.MINOR
    addLabel("tms", "MA-T798");

    await setUnits("imperail"); // typo compiles — the point of the union rule
    expect(await SettingsPage.saveButton.isEnabled()).toBe(false);
  });
});
