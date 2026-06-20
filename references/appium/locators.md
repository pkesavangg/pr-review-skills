# Appium Locators — selector strategy & stability

Rules for WebdriverIO + Appium selectors in any TypeScript Page Object. The single biggest source of brittle mobile E2E tests is locator strategy: deep XPath, index-based paths, and copy-dependent text selectors all break on the smallest UI change. Severity uses the orchestrator's taxonomy (`P0` / `P1` / `P2` / `Nit`).

Locator-strategy preference, strongest → weakest:

1. **Accessibility id** — `~login_button` (maps to `content-desc` on Android, `accessibility identifier` on iOS). Stable across layout *and* locale.
2. Platform resource id — Android `id=com.app:id/login`; iOS predicate/class-chain `-ios predicate string:` / `-ios class chain:`.
3. Single-attribute XPath on a stable attribute (`//*[@resource-id="…"]`).
4. **Last resort:** deep/positional XPath.

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

---

## P1 — Index-based selector (`[1]`, `[2]`, `.get(0)`)

Positional indices encode *current* render order. A new banner, A/B variant, or reordered list silently retargets the test to the wrong element — often passing against the wrong control.

```typescript
const selectorAndroid = "//android.view.View/android.view.View[2]";   // 2nd child today, 1st tomorrow
```

**Sniff.** Selector literals containing `[<digit>]` (XPath index) or `await $$(...)[n]` / `.get(n)` element-array indexing in changed files.

**Fix.** Target by a stable identifying attribute instead of position. When you genuinely need the *nth of a known set*, assert the set size first and comment why the index is safe.

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

**Sniff.** XPath/predicate literals containing `@name=`, `@text=`, `@label=`, or `~` values that are clearly human copy ("Sign Up", "LOG IN") rather than ids.

**Fix.** Use an accessibility id that is independent of displayed text. If text matching is unavoidable, pull the expected string from the same localization source the app uses, not a hardcoded literal.

---

## P2 — Selector parity gap between platforms

A getter that returns a real selector for one platform and a guess/placeholder for the other yields a test that's green on iOS and red (or fake-green) on Android, or vice-versa.

**Sniff.** In a platform-switching getter (`driver.isAndroid ? a : b`), one branch is a stable id and the other is `"..."`, a deep XPath, or obviously copy-pasted from the first platform's namespace.

**Fix.** Provide an equivalently stable locator for both platforms, or gate the test with `skip` on the unsupported platform and file a ticket — don't ship a half-wired selector.

---

## P2 — Duplicated selector literal across files

The same raw selector string copy-pasted into multiple Page Objects means a single UI change requires hunting every copy.

**Sniff.** Identical non-trivial selector literals appearing in more than one changed `*.page.ts`.

**Fix.** Define the selector once (a shared selectors module or a base Page Object getter) and reuse it.

---

## Nit — Selector not encapsulated as a private getter

The project's convention is private getter selectors on the Page Object. Inline `$()` calls scattered through public methods or specs leak locator details out of the POM.

**Fix.** Move the selector into a `private get …()` on the Page Object and reference it from the action method.
