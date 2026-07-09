# Appium Mobile Commands & Context — native/webview boundaries and command currency

Appium exposes device- and app-level commands beyond the WebDriver element API — context
switching, app lifecycle, keyboard, device state. Two defects recur here: **leaving the driver in a
WebView context** so later native steps fail on an innocent test, and **calling legacy
JSONWP / `appium*`-prefixed commands** that Appium 2 has replaced with the unprefixed `driver.*`
mobile commands. Both are grounded in the official [WebdriverIO Appium API](https://webdriver.io/docs/api/appium).
Severity uses the orchestrator's taxonomy.

These rules fire **only when the relevant command actually appears in the diff** — a suite that
never touches contexts or legacy commands never triggers them. If a repo `CLAUDE.md`/`README`
documents its own convention, prefer it and skip the conflicting rule.

---

## P1 — Switched into a WebView context without switching back to native

`switchContext` (WDIO `getContexts()` / `switchContext(name)` per the Appium API) changes where
**every** subsequent command runs. Switch into a `WEBVIEW_*` context and forget to switch back, and
the *next* native interaction — often in a later `it` sharing the same session — fails with a
confusing "element not found," and the failure lands on a test that did nothing wrong. This is a
session-scoped state leak, exactly the order-dependence the `test-structure-and-assertions.md` rules
warn about.

```typescript
await driver.switchContext("WEBVIEW_com.gurus.weight");
await (await this.helpArticleLink).click();
// ...no switch back — every later native step now runs in the webview context
```

**Sniff.** A `switchContext(` / `switchAppiumContext(` to a non-`NATIVE_APP` context on a `+` line in
a changed spec or page, with no matching switch back to `NATIVE_APP` in the same method, a
`finally`, or an `afterEach`.

**Fix.** Restore the native context in a `finally` (or `afterEach`) so a failure inside the webview
block can't strand the session:

```typescript
const webview = (await driver.getContexts()).find((c) => String(c).startsWith("WEBVIEW"));
try {
  await driver.switchContext(webview);
  await (await this.helpArticleLink).click();
} finally {
  await driver.switchContext("NATIVE_APP");
}
```

**Do NOT flag** a switch already paired with a restore in a `finally`/`afterEach`, or a helper whose
documented job is to run a block inside a context and restore it afterward.

---

## P2 — Legacy `appium*`-prefixed command instead of the W3C mobile command

The [Appium API docs](https://webdriver.io/docs/api/appium) mark the `appium`-prefixed device
commands **deprecated** in favour of the unprefixed `driver.<command>()` mobile commands:
`appiumShake`→`shake`, `appiumLock`→`lock`, `appiumUnlock`→`unlock`,
`appiumPressKeyCode`→`pressKeyCode`, `appiumBackground`→`background`, `appiumTouchId`→`touchId`, and
similar. They ride the retired JSONWP endpoints and are removed-on-notice. (Legacy
`touchAction` / `TouchAction` / `MultiAction` is covered in `gestures-and-scrolling.md` — if both
match the same call, post one comment, not two.)

```typescript
await driver.appiumLock(3);         // deprecated
await driver.appiumBackground(5);   // deprecated
```

**Sniff.** `driver\.appium[A-Z]\w*\(` on `+` lines.

**Fix.** Call the modern mobile command:

```typescript
await driver.lock(3);
await driver.background(5);
```

For app-lifecycle (`activateApp` / `terminateApp` / `launchApp` / `closeApp`) and keyboard
(`hideKeyboard` / `isKeyboardShown`) calls inline in a spec, prefer the project's `AppHelper` where
it already wraps them rather than the raw driver command — see `helpers-and-reuse.md`. (A bare
selectorless `driver.*` primitive in a spec is not itself a POM breach per `page-objects.md`; this
rule is about *command currency* and *helper reuse*, not locator leakage.)

---

*Context management and the `appium*`-command deprecations were cross-checked against the official
[WebdriverIO Appium API reference](https://webdriver.io/docs/api/appium). Browser-only and
desktop-only commands on that page were excluded as inapplicable to this native-mobile suite.*
