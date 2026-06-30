# Appium Gestures & Scrolling — reach off-screen elements the durable way

Native screens scroll, and the element a test needs is often below the fold or behind an animation. The two recurring defects here are *acting on an element that was never scrolled into view* and *using gesture APIs that Appium 2 has deprecated or that encode device-specific pixel offsets*. Severity uses the orchestrator's taxonomy.

This project's selectors re-query on every access (`private get`), so prefer condition-based scroll/gesture helpers over fixed coordinates. If a repo `CLAUDE.md`/`README` documents a different gesture convention, prefer it and skip the conflicting rule.

---

## P1 — Acting on a potentially off-screen element without scrolling it into view

A `click()` / `setValue()` on an element that's below the fold throws intermittently ("element not interactable" / "not found") on smaller screens or shorter viewports, while passing on the device the author used. WDIO's auto-wait covers *existence in the source tree*, not *visibility on screen* — a scrollable list renders many nodes the user can't yet touch.

```typescript
// fails on a Pixel 4a but passes on a tall emulator
await (await this.btnAcceptTerms).click();   // button is below the fold until scrolled
```

**Sniff.** A `.click()` / `.setValue()` / `.addValue()` on an element that lives in a known scrollable screen (long form, settings list, `LazyColumn`/`RecyclerView`/`UIScrollView`) with no preceding `scrollIntoView` / scroll gesture / `waitForClickable` in the same method.

**Fix.** Scroll to the element first, then act on the re-queried handle:

```typescript
const el = await this.btnAcceptTerms;
await el.scrollIntoView();          // WDIO scrolls the nearest scrollable ancestor
await el.waitForClickable();
await el.click();
```

For native-only scrolling, prefer the platform mobile gesture (`mobile: scrollGesture` on Android, `mobile: scroll` on iOS) or an `-android uiautomator new UiScrollable(...)` selector that scrolls *and* finds in one step, rather than a manual swipe loop.

---

## P2 — Deprecated `touchAction` / `TouchAction` / `MultiAction` API

`touchAction`, `TouchAction`, and `MultiAction` are **deprecated in Appium 2** (the JSONWP/MJSONWP touch endpoints they ride on are gone). They work today via shims but are removed-on-notice and behave inconsistently across drivers. New gesture code should use the W3C Actions API (`browser.action(...)`) or the driver's `mobile:` gesture commands.

```typescript
await driver.touchAction([                       // deprecated
  { action: "press", x: 200, y: 1000 },
  { action: "moveTo", x: 200, y: 200 },
  "release",
]);
```

**Sniff.** `touchAction(`, `new TouchAction(`, `MultiAction(`, or imports of `TouchAction`/`MultiAction` from `webdriverio`/`@wdio/*` on `+` lines.

**Fix.** Use the modern equivalent:

```typescript
// W3C Actions — portable, supported
await browser
  .action("pointer", { parameters: { pointerType: "touch" } })
  .move({ x: 200, y: 1000 })
  .down()
  .move({ x: 200, y: 200, duration: 300 })
  .up()
  .perform();
```

Or a single high-level driver gesture: `driver.execute("mobile: swipeGesture", { left, top, width, height, direction: "up", percent: 0.75 })` (Android) / `"mobile: swipe"` (iOS).

---

## P2 — Fixed-pixel coordinates / offsets in a gesture

Hardcoded `x`/`y` or swipe start/end points are tied to one device's resolution and density. The same swipe overshoots on a tall phone and undershoots on a small one, producing screen-size-dependent flakiness.

```typescript
await driver.execute("mobile: swipeGesture", { left: 100, top: 1600, width: 200, height: 800, direction: "up", percent: 0.5 });   // 1600 only valid on one device
```

**Sniff.** Numeric pixel literals for `x`/`y`/`left`/`top`/`startX`/`endY`/etc. in gesture/`action` calls in changed files.

**Fix.** Derive offsets from the live window size or the target element's bounds (`const { width, height } = await driver.getWindowRect()`), or scope the gesture to an element rect, so it scales across the device matrix. Reserve absolute coordinates for the rare case with no element to anchor to, and comment why.

---

## P2 — Manual swipe loop where `scrollIntoView` / `UiScrollable` exists

Hand-rolled "swipe up N times until the element appears" loops reimplement scrolling badly: they over- or under-scroll, have no clean termination, and add flake unrelated to what the test verifies.

```typescript
for (let i = 0; i < 10; i++) {                    // brittle, no real stop condition
  if (await el.isDisplayed()) break;
  await driver.execute("mobile: swipeGesture", { /* … */ });
}
```

**Sniff.** A loop containing a swipe/scroll gesture gated on `isDisplayed()`/`isExisting()` in changed files (overlaps the reliability conditional-flow and waits `isDisplayed`-as-wait rules — post one comment).

**Fix.** Use `el.scrollIntoView()` (WDIO finds the scrollable ancestor and stops when the element is in view) or an `-android uiautomator new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(...)` selector that scrolls-and-finds atomically and fails with a clear error if the element never appears.

---

## Nit — Gesture duration/velocity as a bare magic number

`duration: 300`, `percent: 0.75`, `speed: 2500` inline across gesture calls make tuning impossible and hide inconsistent expectations.

**Fix.** Name them (or pull from the shared `timeouts`/gesture-constants module that the waits rules already recommend) so swipe behavior is consistent and self-documenting.

---

*Gesture-API currency (Appium 2 `touchAction` deprecation → W3C Actions / `mobile:` commands) and scroll-into-view guidance were cross-checked against the MIT-licensed [`LambdaTest/agent-skills` webdriverio-skill](https://github.com/LambdaTest/agent-skills); browser-only gesture items were excluded as inapplicable to native mobile.*
