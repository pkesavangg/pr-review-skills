# Appium Reliability & Flakiness — don't paper over nondeterminism

Flaky E2E suites erode trust until people ignore red builds. The fixes are almost always *removing* a source of nondeterminism, not adding a retry on top of it. This file covers retries, swallowed failures, conditional flows, and state isolation. Severity uses the orchestrator's taxonomy.

---

## P1 — `retries` masking a flaky test

Auto-retry (`mochaOpts.retries`) turns an intermittently-failing test green, hiding a real bug or timing defect. It's a blunt instrument, not a fix.

```typescript
// wdio.shared.conf.ts
mochaOpts: {
  ui: "bdd",
  timeout: 60000,
  retries: 2,        // a test that needs 3 tries is signalling a real problem
},
```

**Sniff.** `retries:` > 0 in wdio config or `this.retries(n)` in specs — especially when *raised* in a PR, or paired with newly-added tests.

**Fix.** Treat retries as a last-resort safety net, not the default. When a test needs retries, find the race (missing wait, order-dependence, shared state) and fix that. If a small retry count is an accepted policy, it should be documented and justified — flag any increase for explicit sign-off.

---

## P1 — Swallowed failure (`try/catch` that hides errors)

Catching an error and continuing (or logging and moving on) makes a broken step look successful. Tests should fail loudly.

```typescript
try {
  await (await this.btnSubmit).click();
} catch (e) {
  console.log("submit not found, continuing");   // test now can't fail here
}
```

**Sniff.** On `+` lines: `try { … } catch` in test/page files where the `catch` doesn't re-throw or `expect.fail` — particularly `catch {}` empty blocks or `catch` that only `console.log`/`warn`s and continues.

**Fix.** Let the error propagate, or convert it into an explicit assertion failure with context. When you must catch to tolerate an *absent* element, use the project's `ElementHelper.isElementNotFoundError(e)` guard so a genuine session/protocol failure still re-throws:

```typescript
try { await driver.hideKeyboard(); }
catch (e) { if (!isElementNotFoundError(e)) throw e; }   // absent keyboard OK; dead session isn't
```

**Do NOT flag** a catch that guards on `isElementNotFoundError` (or re-throws non-absent errors), or a documented expected-error scenario that re-asserts. The defect is the *unconditional* swallow, not every catch.

---

## P1 — `.catch(() => false)` whose result feeds a test assertion

The inline form of a swallowed failure, and the single highest-leverage root-cause fix in this suite. `await el.isDisplayed().catch(() => false)` returns `false` for **every** rejection — including a crashed Appium session or a protocol error. When that boolean flows into an assertion, a *dead driver reads as "element not present" and the test passes green on a broken session*. The project wrote `ElementHelper` specifically to close this hole (its docstrings say so): `isDisplayedSafe` / `isDisplayedNow` return `false` only for genuine "not found", and re-throw session/protocol errors.

```typescript
// login.spec.ts — a session crash here makes the test PASS, not fail
const loggedOut = await LoginPage.landing.isDisplayed().catch(() => false);
expect(loggedOut).toBe(true);
```

**Sniff.** On `+` lines: `.catch(() => false)`, `.catch(() => {})`, `.catch(() => undefined)` where the value flows into an `expect(` / `return` / `if` that gates a test outcome. Also flag a **retry hidden inside a `.catch`** (`.catch(async () => { await reset(); await retry(); })`) — that's the `retries`-masking defect in disguise.

**Fix.** Route the check through `ElementHelper`, which surfaces real failures:

```typescript
import { isDisplayedSafe, isDisplayedNow, swallowNotFound } from "../helpers/ElementHelper";

const loggedOut = await isDisplayedNow(LoginPage.landing);   // absent → false; session error → throws
expect(loggedOut).toBe(true);
```

**Do NOT flag** a `.catch(() => false)` used as a **probe inside a scroll/settle loop** (e.g. the base `Page.scrollToElement` "is the target on screen yet?" checks) where the boolean only drives *whether to keep scrolling*, not a pass/fail assertion — that's an accepted pattern in this repo.

---

## P2 — Conditional control flow hiding nondeterminism

`if (await el.isDisplayed()) { … }` branches make the test do different things on different runs — the assertion you think runs may be skipped.

```typescript
if (await dialog.isDisplayed()) {
  await dialog.dismiss();        // sometimes runs, sometimes not → unstable coverage
}
await page.continue();
```

**Sniff.** `if (await …isDisplayed())` / `isExisting()` gating test steps or assertions.

**Fix.** Make the precondition deterministic (wait for the dialog you *expect*, then assert+dismiss). Genuinely-optional system dialogs (permissions) are better handled via capabilities (`autoGrantPermissions`, `appium:permissions`) than runtime branching.

---

## P1 — No clean state between tests

Without an app reset between specs, residual state (logged-in session, cached input, navigation stack) leaks across tests and creates order-dependent failures. This is the *config/lifecycle* side of the same defect that `test-structure-and-assertions.md` flags as **P1 — Order-dependent / shared-state test** (the *spec* side); keep the two at the same severity and, when both fire on one PR, post a single comment rather than two.

**Sniff.** New multi-test specs with no reset strategy, or capabilities setting `noReset: true` / `fullReset: false` without a matching in-test reset.

**Fix.** Establish known state per test — relaunch/reset in `beforeEach`, or `driver.terminateApp`/`activateApp`. Decide a `noReset` policy deliberately and document it. When running parallel (`maxInstances > 1`), isolate each test's data with unique identifiers (per-run user/email/record) so concurrent sessions don't collide on shared backend state.

---

## P2 — Repeated setup flow not extracted / UI used where an API shortcut exists

Driving multi-step setup (log in, seed state) through the UI in every test is slow and adds a flake surface that has nothing to do with what the test verifies. Repeated inline sequences also drift out of sync.

```typescript
// every test logs in through the UI just to reach the screen under test
await LandingPage.clickLogin();
await LoginPage.login(user, pass);   // 6 UI steps × N tests = slow + flaky
```

**Sniff.** The same multi-step interaction copy-pasted across specs, especially auth/setup that isn't the actual subject of the test.

**Fix.** Extract reusable flows into a WDIO custom command (`browser.addCommand('loginViaApi', …)`) or a shared helper, and prefer an **API/deep-link shortcut** to reach the screen under test when login itself isn't what's being verified. Reserve the full UI flow for the tests that actually assert on it.

---

## P2 — Failure diagnostics captured but not attached

This project screenshots on failure (`afterTest`), but a screenshot that isn't attached to the Allure report is hard to find later.

```typescript
afterTest: async function (test, context, { error }) {
  if (error) {
    await browser.takeScreenshot();   // captured but not attached to the report
  }
}
```

**Sniff.** `takeScreenshot()` whose result isn't passed to `allure.addAttachment` / reporter, or diagnostics gated so they don't run on all failures.

**Fix.** Attach the screenshot (and ideally page source) to the report on failure so triage is one click. The video reporter already links recordings — make screenshots equally discoverable.

---

## Nit — `bail` / instance settings that fight CI signal

`bail: 0` runs the whole suite after an environmental failure (device offline), flooding the report; very high `connectionRetryCount` can hide real connectivity issues.

**Fix.** Tune `bail`/retry counts to your CI intent and comment the rationale.

---

*The custom-command / API-shortcut and parallel-isolation guidance was cross-checked against the MIT-licensed [`LambdaTest/agent-skills` webdriverio-skill](https://github.com/LambdaTest/agent-skills); browser-only items were excluded as inapplicable to native mobile.*
