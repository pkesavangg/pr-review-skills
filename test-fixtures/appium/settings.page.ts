// Fixture for references/appium/locators.md (the mandatory id-vs-text check)
// and references/code-standards/typescript.md. Deliberate triggers — do not fix.
//
// Expected findings:
//   P1  element located by visible copy when an automation id exists on that platform
//   P2  text-dependent selector where no id exists yet, with no tracked TODO
//   P2  platform-varied literal pair should use platformLocator()
//   P2  public selector leaking out of the page object
//   P2  magic numbers / mutable exported object missing `as const`
//   Nit missing explicit return type on a public method

import Page from "./page";

// P2 — exported mutable lookup table: importers can write to it, and the values
//      widen to `number`, so it can't drive a union. Needs `as const`.
export const MY_KIDS_TIMEOUTS = { SHORT: 5000, LONG: 30000 };

class SettingsPage extends Page {
  // P1 — the app ships accessibilityIdentifier "settings_my_kids_row" on iOS and
  //      testTag "settings_my_kids_row" on Android, but both branches match copy.
  private get rowMyKids() {
    return $(
      driver.isAndroid
        ? 'android=new UiSelector().text("My Kids")'
        : '//XCUIElementTypeStaticText[@name="My Kids"]',
    );
  }

  // P2 — no testTag exists for this control yet, and no `// TODO(<TICKET>)` note
  //      records the debt.
  private get emptyStateMessage() {
    return $('//*[contains(@text,"You haven\'t added any kids yet")]');
  }

  // P2 — both branches are plain literals: use platformLocator(android, ios)
  private get buttonBack() {
    return $(driver.isAndroid ? "~appBarBack" : "~chevronLeft");
  }

  // P2 — public selector: callers can bypass the action methods
  public get saveButton() {
    return $("~settings_save_button");
  }

  // Nit — no explicit Promise<void> return type on a public action
  public async openMyKids() {
    await this.rowMyKids.waitForDisplayed({ timeout: 15000 }); // P2 — magic number; use TIMEOUTS.MEDIUM
    await this.rowMyKids.click(); // P2 — waitForDisplayed→click is base Page.tapWhenReady
  }

  public async readEmptyState(): Promise<string> {
    return this.emptyStateMessage.getText();
  }
}

export default new SettingsPage();
