# Appium Waits & Synchronization — kill the flake at its source

Mobile UIs are asynchronous: animations, network, and view inflation all race the test runner. The #1 cause of flaky Appium suites is **implicit timing assumptions** — fixed sleeps and acting on not-yet-ready elements. Severity uses the orchestrator's taxonomy.

This project has a base `waitForElement(selector, timeout)` helper — prefer it (or WDIO's built-in `waitFor*`) over any hardcoded delay.

---

## P1 — Hardcoded sleep / `pause` instead of an explicit wait

`driver.pause(2000)` either wastes 2s when the element is ready in 200ms, or fails when a slow CI runner needs 2.5s. It encodes a guess about timing, not a condition.

```typescript
await driver.pause(3000);            // flake + slow
await (await this.buttonLogin).click();
```

**Sniff.** `driver.pause(`, `browser.pause(`, `await pause(`, `setTimeout(`, `new Promise(r => setTimeout` in changed test/page files.

**Fix.** Wait for the *condition* you actually depend on:

```typescript
await this.waitForElement(await this.buttonLogin);   // waitForDisplayed under the hood
await (await this.buttonLogin).click();
```

Exception: a deliberately tiny settle after an animation with no observable end-state — flag as **Nit** and ask for a `waitUntil` on a real signal instead.

---

## P1 — Action on an element without waiting for readiness

`click()` / `setValue()` on an element that may not yet be displayed/enabled throws intermittently ("element not interactable", "not found"). WDIO's auto-wait covers *existence* via `waitforTimeout`, but not *displayed/enabled/stable*.

```typescript
await (await this.btnSubmit).click();   // no guarantee it's rendered & enabled yet
```

**Sniff.** `.click()` / `.setValue()` / `.addValue()` / `.touchAction(` on `+` lines where the immediately preceding lines don't wait for that element (`waitForElement`, `waitForDisplayed`, `waitForEnabled`).

**Fix.** Match the wait to the interaction:

- Before **tap/click** → `waitForClickable()` (an element can be *displayed* but still overlapped, animating, or disabled — `waitForDisplayed` alone lets "element not interactable" through).
- Before **assertions and `setValue`/typing** → `waitForDisplayed()` (and `waitForEnabled()` for inputs).

This project's base `waitForElement` wraps `waitForDisplayed`; add a `waitForClickable` helper for tap targets.

---

## P1 — Assertion relying on implicit timing instead of `waitUntil`

Asserting state immediately after an action ("expect displayed" right after a navigation) races the UI. The assertion should *poll* for the expected condition.

```typescript
await page.submit();
expect(await dashboard.isDisplayed()).toBe(true);   // checks once, immediately
```

**Sniff.** `expect(...)` reading a live UI state on the line right after an action, with no `waitForDisplayed`/`waitUntil` in between.

**Fix.** Use WDIO's retrying matchers (`await expect(el).toBeDisplayed()`), which poll up to `waitforTimeout`, or wrap a custom condition in `driver.waitUntil(async () => …)`.

---

## P1 — Cached element handle reused after a re-render (stale element)

Storing an element in a variable and reusing it after the view tree changes throws `StaleElementReferenceException` — common on mobile when a list recycles (RecyclerView / `LazyColumn`), a Compose screen recomposes, or navigation replaces the view.

```typescript
const row = await $('~item_3');
await row.click();          // navigates / re-renders the list
await row.getText();        // STALE — `row` points at a detached element
```

**Sniff.** A `const el = await $(...)` (or `await this.someSelector`) reused across an action that mutates/re-renders the screen, rather than re-querying.

**Fix.** Re-query each time you touch the element — exactly what this project's `private get` selectors do (they call `$()` on every access). Don't hoist an element handle across a state change; access the getter again. WDIO auto-retries the *lookup*, not a stale handle you cached.

---

## P2 — Magic timeout numbers scattered inline

`waitForDisplayed({ timeout: 15000 })`, `30000`, `5000` sprinkled across files make tuning impossible and hide inconsistent expectations.

**Sniff.** Numeric `timeout:` literals (or bare ms numbers passed to waits) repeated across changed files.

**Fix.** Centralize in a `timeouts` constants module and reference named tiers instead of magic numbers — a workable convention: `SHORT ≈ 5s` (a control that should already be present), `MEDIUM ≈ 10–15s` (normal element appearance — the base `waitForElement` default), `LONG ≈ 30s` (app launch / cold start / first network call). Bump thresholds (or rely on a higher `waitforTimeout`) on CI, where hardware is slower than a dev laptop.

---

## P2 — `isExisting()` / `isDisplayed()` used as a wait

Polling these in a manual loop, or treating a single `isDisplayed()` as "it's ready", reimplements waiting badly and usually still races.

**Fix.** Use `waitForExist` / `waitForDisplayed` / `waitUntil`, which retry with a clear timeout and produce a useful error message on failure.

---

## Nit — `waitForDisplayed()` without an explicit timeout in a context with non-default needs

Relying on the implicit default is fine generally, but slow first-launch / cold-start screens often need a longer, intentional timeout.

**Fix.** Pass an explicit named timeout for known-slow screens (app launch, first network call) and comment why.

---

*Some guidance here (clickable-vs-displayed waits, stale-element re-query, CI timeout sizing) was cross-checked against the MIT-licensed [`LambdaTest/agent-skills` webdriverio-skill](https://github.com/LambdaTest/agent-skills); browser-only items (network mocks, iframes, cookies, visual regression) were intentionally excluded as inapplicable to native mobile.*
