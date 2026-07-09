# Appium Locators — selector strategy & stability

Rules for WebdriverIO + Appium selectors in any TypeScript Page Object. The single biggest source of brittle mobile E2E tests is locator strategy: deep XPath, index-based paths, and copy-dependent text selectors all break on the smallest UI change. Severity uses the orchestrator's taxonomy (`P0` / `P1` / `P2` / `Nit`).

Locator-strategy preference, strongest → weakest:

1. **Accessibility id** — `~login_button` (maps to `content-desc` on Android, `accessibility identifier` on iOS). Stable across layout *and* locale.
2. Platform resource id — Android `id=com.app:id/login`; iOS predicate/class-chain `-ios predicate string:` / `-ios class chain:`.
3. **Single-attribute XPath on a *stable identity* attribute** (`//android.widget.EditText[@password="true"]`, `//*[@resource-id="…"]`) — an attribute that describes *what the element is*, not where it sits. This is a legitimate target, not a smell.
4. Visible-text match (`text("LOG IN")`, `@name="Sign up"`) — couples to copy/locale.
5. **Last resort:** deep/positional XPath (`…/View/View[2]`).

**Platform reality in this project (important — it drives which tier is even reachable):**

- **iOS** — the app ships `accessibilityIdentifier`s, so tests should target them via `-ios predicate string:name == "login_submit_button"` (tier 1/2). Matching visible copy on iOS when an id exists is a real finding.
- **Android** — the app is **Jetpack Compose and currently exposes no `testTag`/`resource-id`** on most screens. So tier 1/2 often *isn't available yet*, and anchoring on a stable **identity attribute** (tier 3, e.g. `@password="true"`) — or, when nothing else exists, text (tier 4) — is the pragmatic best, **provided** it's the most stable attribute available and carries a `// TODO(<TICKET>): replace with testTag` note. That's tracked debt, not a defect. The durable fix is a request to the app team for Compose `testTag`s, which would lift most Android selectors up to tier 1.

If a repo `CLAUDE.md` or `README` documents a different convention, prefer it and skip the conflicting rule.

---

## P1 — Brittle absolute / deep XPath locator

A long positional XPath chain breaks the instant any wrapping view, ordering, or hierarchy depth changes — extremely common with Compose/SwiftUI view trees, which re-nest freely between builds.

```typescript
// landing.page.ts — fragile
private get buttonLogin() {
  const selectorAndroid =
    "//androidx.compose.ui.platform.ComposeView/android.view.View/android.view.View/android.view.View[1]/android.widget.Button";
  const selectorIOS = '//XCUIElementTypeButton[@name="LOG IN"]';
  return $(driver.isAndroid ? selectorAndroid : selectorIOS);
}
```

**Sniff.** In changed `*.page.ts` / selector strings, flag XPath with **3+ chained element steps**, any `android.view.View`/`XCUIElementType*` hierarchy walk, or a leading `//` followed by a deep path. Strong signal: the literal contains `ComposeView`, `android.view.View/`, or `/android.widget`.

**Fix.** Ask the app team to add a stable `testTag` (Compose) / `accessibilityIdentifier` (SwiftUI), then target it: `$('~login_button')`. If the build can't be changed yet, use the most stable single attribute available and add a `// TODO: replace with accessibility id (<TICKET>)`.

**Do NOT flag a single-step identity-attribute XPath.** `//android.widget.EditText[@password="true"]` or `//*[@resource-id="com.app:id/x"]` is **one** element step keyed on an identity attribute — that's tier 3 in the hierarchy above and the *recommended* fallback where no id exists, not a deep-path defect. The target of this rule is the *multi-step positional walk*, not any XPath.

---

## P1 — Index-based selector (`[1]`, `[2]`, `.get(0)`)

Positional indices encode *current* render order. A new banner, A/B variant, or reordered list silently retargets the test to the wrong element — often passing against the wrong control.

```typescript
const selectorAndroid = "//android.view.View/android.view.View[2]";   // 2nd child today, 1st tomorrow
```

**Sniff.** Selector literals containing `[<digit>]` (XPath index) or `await $$(...)[n]` / `.get(n)` element-array indexing in changed files.

**Fix.** Re-anchor on a stable **identity attribute** — what the element *is*, not where it sits. This repo already did exactly this upgrade: `//android.widget.EditText[2]` (positional — shifts if a field is inserted above) → `//android.widget.EditText[@password="true"]` (identity — the masked field, stable across focus/reveal). Prefer a `testTag`/`accessibilityIdentifier` when one exists; otherwise a unique attribute (`@content-desc`, `@resource-id`, a boolean like `@password`). When you genuinely need the *nth of a known set*, assert the set size first and comment why the index is safe.

---

## P1 — Placeholder / empty selector merged

A selector left as `"..."`, `""`, or `TODO` ships a Page Object that cannot work — the test either throws an invalid-selector error or silently no-ops.

```typescript
// login.page.ts — non-functional, must not merge
private get inputUsername() {
  const selectorAndroid = "...";
  const selectorIOS = "...";
  return $(driver.isAndroid ? selectorAndroid : selectorIOS);
}
```

**Sniff.** Selector string literals equal to `"..."`, empty `""`, `"TODO"`, or `"changeme"` on `+` lines in `*.page.ts`.

**Fix.** Supply the real accessibility id for both platforms before merge, or remove the unused getter. If the screen isn't built yet, the Page Object shouldn't be in the PR.

---

## P2 — Text / copy-dependent selector

Matching on visible text (`@name="LOG IN"`, `@text="Sign up"`) couples the test to product copy and breaks under localization or a wording tweak.

```typescript
const selectorIOS = '//XCUIElementTypeButton[@name="LOG IN"]';   // breaks on copy/locale change
```

**Sniff.** XPath/predicate literals containing `@name=`, `@text=`, `@label=`, `contains(@text,…)`, `UiSelector().text(…)`, or `~` values that are clearly human copy ("Sign Up", "LOG IN") rather than ids.

**Fix.** Use an accessibility id that is independent of displayed text. If text matching is unavoidable, pull the expected string from the same localization source the app uses, not a hardcoded literal.

**Flag vs. accepted — judge per platform, per the reality note above:**

- **Flag** text/copy matching **when a stable id was available on that platform** — e.g. iOS ships `login_submit_button` but the getter matches `@name == "LOG IN"` copy anyway; or new text-coupling added on a screen that *does* expose ids.
- **Do NOT flag** a text (or identity-attribute) selector used **because the target has no id on that platform** — the norm on this project's Android Jetpack Compose screens — **when** it anchors on the most stable option available *and* carries a `// TODO(<TICKET>): ask dev for a testTag` note. That's documented, tracked debt (see `login.page.ts` `errorMessage`, `MOB-1417`), not a fresh defect. The right move is to keep the ticket alive, not to re-flag every occurrence.

Either way, when text is the only option, the reviewer's standing recommendation is: **ask the app team to add a Compose `testTag` / SwiftUI `accessibilityIdentifier`** — the one change that converts the largest number of fragile text selectors into stable `~id` ones.

---

## P2 — Selector parity gap between platforms

A getter that returns a real selector for one platform and a guess/placeholder for the other yields a test that's green on iOS and red (or fake-green) on Android, or vice-versa.

**Sniff.** In a platform-switching getter (`driver.isAndroid ? a : b`), one branch is a stable id and the other is `"..."`, a deep XPath, or obviously copy-pasted from the first platform's namespace.

**Fix.** Provide an equivalently stable locator for both platforms, or gate the test with `skip` on the unsupported platform and file a ticket — don't ship a half-wired selector.

---

## P2 — Duplicated selector literal across files

The same raw selector string copy-pasted into multiple Page Objects means a single UI change requires hunting every copy. This project already has the shared home for these: `test/helpers/selectors.ts` (e.g. `ANDROID_CANCEL_BUTTON`).

**Warranted vs. inline — apply the project's threshold.** Per this repo's `CLAUDE.md` (and `selectors.ts`'s own header), a selector used by **one** page belongs **inline in that page's getter** — the platform branching and per-selector rationale stay co-located. A shared constant is warranted **only** when the same id/label is reused **across pages/helpers**. So:

- **Flag** a selector literal that appears on `+` lines in **≥2 changed `*.page.ts`**, or a literal being re-inlined that **already exists in `selectors.ts`** (e.g. re-typing `'android=new UiSelector().text("CANCEL")'` instead of importing `ANDROID_CANCEL_BUTTON`).
- **Do NOT flag** a single-use selector living inline in one page's getter — pushing that into a shared module fights the documented convention and scatters one page's locators.

**Fix.** Move the cross-page literal into `test/helpers/selectors.ts` and import it in each page; leave single-use selectors inline.

---

## P2 — Platform-varied selector pair should use `platformLocator`

A getter that returns `$(driver.isAndroid ? '<android string>' : '<ios string>')` re-implements platform branching the project has already abstracted: `platformLocator(android, ios)` in `test/helpers/PlatformHelper.ts` (used 500+ times across the suite). The inline ternary is fine as a one-off, but when it's the *only* thing the getter does — pick one of two selector strings by platform — `platformLocator` states intent in one call and keeps branching uniform at scale.

```typescript
// pure string pair — prefer the helper
private get backButton() {
  return $(driver.isAndroid ? '~appBarBack' : '~chevronLeft');
}
```

**Sniff.** `$(driver.isAndroid ? '<literal>' : '<literal>')` / `$(isAndroid() ? '<literal>' : '<literal>')` — **both branches string literals** — on `+` lines in `*.page.ts`.

**Fix.** `return $(platformLocator('~appBarBack', '~chevronLeft'));`

**Do NOT flag** a getter whose branches contain **method calls or differing logic** (e.g. one platform scrolls-and-finds, the other predicate-matches) — inline branching with co-located rationale is the documented POM convention there. This rule targets only the redundant two-literals-by-platform case.

---

## Nit — Selector not encapsulated as a private getter

The project's convention is private getter selectors on the Page Object. Inline `$()` calls scattered through public methods or specs leak locator details out of the POM.

**Fix.** Move the selector into a `private get …()` on the Page Object and reference it from the action method.
