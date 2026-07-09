# Appium Waits & Synchronization — kill the flake at its source

Mobile UIs are asynchronous: animations, network, and view inflation all race the test runner. The #1 cause of flaky Appium suites is **implicit timing assumptions** — fixed sleeps and acting on not-yet-ready elements. Severity uses the orchestrator's taxonomy.

This project has a base `waitForElement(selector, timeout)` helper — prefer it (or WDIO's built-in `waitFor*`) over any hardcoded delay.

---

## P1 — `pause` added as a substitute for a wait condition

`driver.pause(3000)` before an interaction either wastes 3s when the element is ready in 200ms, or fails when a slow CI runner needs 3.5s. It encodes a guess about timing, not a condition — and a pause *added in a PR right before a `click`/`setValue`/`expect`* is the classic band-aid for a race the author should instead wait out.

```typescript
await driver.pause(3000);            // guessing the button is ready — flake + slow
await (await this.buttonLogin).click();
```

**Scope this to the diff, and only the band-aid shape.** This repo legitimately uses `driver.pause` in the thousands, almost all as *deliberate settles* (the `WAIT` constants module in `test/data/constants.ts` exists for exactly this). Do **not** sweep every pause. Flag a pause only when it is:

- **Added or modified on a `+` line** in this PR (pre-existing settles are out of scope), **and**
- Standing in for a wait — it sits *immediately before* a `.click()` / `.setValue()` / `.addValue()` / `expect(` / navigation, gating readiness the code should instead poll for.

**Sniff.** On `+` lines: `driver.pause(`, `browser.pause(`, `await pause(`, `setTimeout(`, `new Promise(r => setTimeout` — then check the *next* statement is an interaction/assertion on a not-yet-waited element.

**Fix.** Wait for the *condition* you actually depend on:

```typescript
await this.tapWhenReady(await this.buttonLogin);     // base Page: waitForDisplayed → click
// or, for a non-tap dependency:
await this.waitForElement(await this.buttonLogin);
await (await this.buttonLogin).click();
```

For app-state settles, poll the real signal — `AppHelper` already models this (`queryAppState`, `waitForKeyboardHidden`) instead of a fixed sleep.

**Do NOT flag (accepted patterns):** a small, **documented** settle *after* a fling/animation/native transition with no observable end-state (e.g. `await driver.pause(WAIT.SETTLE); // settle after slide animation, no end-state to waitFor`); pauses inside the base `Page` scroll helpers or `GestureHelper`; a `WAIT.*`-named settle whose comment explains why a condition wait isn't possible. When such a settle uses a raw number instead of a `WAIT.*` key, that's the magic-number Nit below, not this P1.

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

This project's base `Page` already provides `tapWhenReady` (waitForDisplayed → click) — use it for buttons/rows instead of an unguarded `.click()`. `waitForElement` wraps `waitForDisplayed` for the assert/typing case. For tap targets prone to overlap/animation, prefer a `waitForClickable` before the tap (add a base-`Page` helper if one isn't there yet).

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

## P2 — Timeout raised instead of fixing the wait condition

A timeout *bumped upward in a PR* — `timeout: 15000 → 120000`, a larger `waitforTimeout`/`connectionRetryTimeout` in config, or a new pause bigger than the `TIMEOUTS.LONG` tier — is a band-aid tell. A senior reviewer's question is *"what got slower, or which race are you hiding?"* Padding the timeout makes the symptom disappear locally while the underlying sync gap ships (and every future run pays the longer worst-case). This is distinct from the pause rule: here the wait *exists*, the author just widened its budget instead of fixing what it waits on.

```typescript
// diff: was { timeout: 15000 }
await el.waitForDisplayed({ timeout: 120000 });   // why 8× longer now?
```

**Sniff.** A `timeout:` / `waitforTimeout` / `connectionRetryTimeout` numeric literal that *increased* across a `-`/`+` diff pair, or a new wait/pause whose value exceeds the project's `TIMEOUTS.LONG`. Watch commit messages too ("bump timeouts", "raise … for slow … flow").

**Fix.** Find what the wait is racing — a missing `waitForClickable`, a screen that isn't ready, an element that re-renders (stale) — and wait on *that* signal. If a longer wait is genuinely warranted (documented cold-start, a known-slow native picker), use a **named** `TIMEOUTS` tier and justify the choice in the PR so the reason travels with the number. A bare bumped literal with no rationale should not merge.

---

## P2 — Magic timeout numbers scattered inline

`waitForDisplayed({ timeout: 15000 })`, `30000`, `5000` sprinkled across files make tuning impossible and hide inconsistent expectations. This project already centralizes both wait tiers and settle durations — raw numbers duplicate what those modules exist to hold.

**Sniff.** Numeric `timeout:` literals (or bare ms numbers passed to waits), or raw `driver.pause(<number>)` durations, on `+` lines where a named constant applies.

**Fix.** Reference the project's existing constants instead of a literal:

- **Wait timeouts** → `TIMEOUTS` (`test/helpers/timeouts.ts`): `SHORT ≈ 5s` (a control that should already be present), `MEDIUM ≈ 15s` (normal element appearance — the base `waitForElement` default), `LONG ≈ 25s` (launch / auth routing / multi-step dialogs).
- **Settle durations** → `WAIT` (`test/data/constants.ts`): `WAIT.SETTLE`, `WAIT.NETWORK`, `WAIT.DIALOG`, etc.

Don't introduce a third constants object — see `helpers-and-reuse.md` (Nit — wrong module). Rely on a higher `waitforTimeout` on CI, where hardware is slower than a dev laptop, rather than bumping individual call sites.

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
