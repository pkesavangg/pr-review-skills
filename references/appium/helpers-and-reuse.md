# Appium Helpers & Reuse — use the project's toolbox, don't re-roll it

A mature Appium suite grows a shared toolbox: a base `Page`, platform helpers, auth/navigation flows, gesture and element utilities, and centralized constants. The scalability failure mode is not *missing* those helpers — it's authors **re-implementing them inline** because they didn't know the helper existed. That quietly forks behavior: the helper gets a bug fix (a WDA-hang guard, a stale re-query, a settle) and the inline copy doesn't. Severity uses the orchestrator's taxonomy.

This file is the reuse lens for §4a.6 ("correct use of the helpers instead of re-rolling them"). Before flagging, confirm the helper genuinely exists in the branch under review (grep `test/helpers/` and `test/pageobjects/page.ts`) — a repo may have renamed or removed one. If a repo `CLAUDE.md`/`README` documents a different toolbox, prefer it.

---

## The meApp helper ecosystem (the canonical map)

When a diff re-implements any of the below, the fix is *"call the existing helper"*, naming the exact symbol:

| Concern | Module / symbol | What it already does |
|---|---|---|
| App lifecycle | `AppHelper` (`test/helpers/AppHelper.ts`) | `launchApp` / `restartApp` / `resetApp` / `backgroundApp` / `backgroundAppForSeconds` / keyboard helpers (`isKeyboardShown`, `pressKeyboardNext`, `waitForKeyboardHidden`) / `getInstalledVersionName` — all with WDA-hang guards and `queryAppState` polling instead of fixed sleeps |
| Login / auth flow | `AuthHelper` (`test/helpers/AuthHelper.ts`) | `loginAs(email, password)`, `loginWithValidCredentials()`, `loginFromLandingIfPresent()`, `isOnMultiAccountScreen()`, `tapMultiAccountTileIfPresent()` |
| Reaching the login screen | `NavigationHelper.navigateToLoginScreen()` (`test/helpers/NavigationHelper.ts`) | Deterministic route to the login screen with retry/recovery |
| Scrolling / gestures | `GestureHelper` (`test/helpers/GestureHelper.ts`) | Device-tuned scroll/swipe (handles button-nav devices via `BUTTON_NAV_UDIDS`), multi-tap bursts, iOS `mobile: swipe` fallbacks |
| Platform branching | `platformLocator(android, ios)`, `isAndroid()`, `isIOS()`, `getPlatform()` (`test/helpers/PlatformHelper.ts`) | Returns the right selector string per platform in one call |
| Safe visibility checks | `ElementHelper` (`test/helpers/ElementHelper.ts`) | `isDisplayedSafe` / `isDisplayedNow` / `swallowNotFound` / `isElementNotFoundError` — distinguish "element absent" (→ false) from "session/protocol error" (→ re-throw) |
| Shared interaction | base `Page` (`test/pageobjects/page.ts`) | `waitForElement`, `tapWhenReady` (waitForDisplayed → click), `scrollToElement`, `scrollPickerColumnToValue`, `verifyErrorMessage`, `dismissUnableToScanDialog` |
| Pause durations | `WAIT` (`test/data/constants.ts`) | Named settle durations (`WAIT.SETTLE`, `WAIT.NETWORK`, …) — for deliberate settles only, not condition waits |
| Wait timeouts | `TIMEOUTS` (`test/helpers/timeouts.ts`) | `SHORT` / `MEDIUM` / `LONG` tiers for `waitForDisplayed`/`waitUntil` |

---

## P2 — Re-rolling an existing helper or base-`Page` method

Reimplementing a capability the toolbox already provides forks behavior and skips the accumulated hardening (WDA-hang races, stale re-queries, device-specific gesture tuning). This is the most common organization defect in a mature suite — the author solved a problem the base class already solved.

```typescript
// signup-foo.page.ts — re-rolls waitForDisplayed → click that base Page already provides
public async clickContinue() {
  const btn = await this.buttonContinue;
  await btn.waitForDisplayed({ timeout: 15000 });
  await btn.click();
}

// some.page.ts — hand-rolls an app relaunch instead of AppHelper.restartApp()
await driver.terminateApp(appId, {});
await driver.pause(1000);
await driver.activateApp(appId);
```

**Sniff.** On `+` lines in changed `*.page.ts` / `*.spec.ts`, a hand-written sequence that duplicates a named helper: a `waitForDisplayed(...)` immediately followed by `.click()` on the same element (→ `tapWhenReady`); a `terminateApp`/`activateApp` pair (→ `AppHelper.restartApp`/`resetApp`); an inline `driver.isKeyboardShown()` poll loop (→ `AppHelper.waitForKeyboardHidden`); a hand-written scroll-swipe loop in a page object (→ base `Page.scrollToElement` / `GestureHelper`).

**Fix.** Call the existing helper by name — e.g. `await this.tapWhenReady(await this.buttonContinue)`, `await AppHelper.restartApp()`. Only re-implement when the helper genuinely can't express the case, and say why in a comment. **Do not flag** the helper's *own* definition, or a page whose need is materially different from what the helper offers.

---

## P2 — Setup / login flow re-implemented inline instead of `AuthHelper` / `NavigationHelper`

Copy-pasting a multi-step login (restart → wait for landing → dismiss popups → `LoginPage.login` → wait for dashboard) into a spec — or defining a spec-local `loginAs` — drifts out of sync with `AuthHelper` the moment the real flow changes (a new post-login overlay, a multi-account tile). Each copy is a separate maintenance liability and a separate flake surface.

```typescript
// history.spec.ts — a full private copy of what AuthHelper.loginAs already does
async function loginAs(email: string, password: string) {
  await AppHelper.restartApp();
  await LandingPage.waitForLanding(8000).catch(() => {});
  // …logout-if-needed, clickLogin, login, dismiss popups, waitForDashboard…
}
```

**Sniff.** In changed `*.spec.ts`: a spec-local `function login…` / `async function loginAs`, or an inline `restartApp()` → `waitForLanding` → `LoginPage.login(...)` sequence, when `AuthHelper` already exports the equivalent. Also flag repeated `NavigationHelper`-shaped navigation copied inline.

**Fix.** `import { loginAs, loginWithValidCredentials } from "../helpers/AuthHelper"` (and `NavigationHelper.navigateToLoginScreen()` to reach the login screen) and call it. If a spec needs a genuinely different setup, extract *that* into a new shared helper rather than a spec-local one so the next spec can reuse it. **Do not flag** a thin spec-local wrapper that only *delegates* to the imported helper (e.g. a one-line `loginWithValidCredentials()` shim).

---

## Nit — New timing constant added to the wrong module

The suite has two centralized timing modules with distinct jobs: `WAIT` (`test/data/constants.ts`) for deliberate `driver.pause()` settle durations, and `TIMEOUTS` (`test/helpers/timeouts.ts`) for `waitForDisplayed`/`waitUntil` tiers. Adding a *third* constant object, or putting a wait-timeout in `WAIT` / a settle duration in `TIMEOUTS`, re-fragments what these modules exist to centralize.

**Sniff.** A new `const … = { … }` of ms values in a page/spec, or a new timing key added to the module that doesn't match its role.

**Fix.** Add the value to the correct existing module (`WAIT` for a settle, `TIMEOUTS` for a condition wait) and reference the named key.
