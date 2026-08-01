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

**The id-vs-text check is mandatory on every changed selector.** It is the most frequently violated rule in this suite, and it splits in two by severity: *an id exists on that platform and the test matched copy anyway* → **P1** (below); *no id exists yet* → **P2**, fixed by anchoring on the best available attribute plus a tracked `// TODO(<TICKET>)`. Decide which by actually checking for an id — don't assume either way.

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

## P1 — Element located by visible text when an automation id exists on that platform

**This check runs on every changed selector — it is the single most common standards violation in this suite, so never skip it.** Picking an element by the words the user sees couples the test to product copy: a wording tweak, a sentence-case change, a trailing-space fix, or the first localized build turns a green test red for a reason that has nothing to do with the app being broken. When the control *already ships an automation id* on that platform, matching its copy instead is simply the wrong locator — the stable handle was right there.

```typescript
// login.page.ts — iOS ships accessibilityIdentifier "login_submit_button", but the test matches copy
private get buttonLogin() {
  const selectorIOS = '//XCUIElementTypeButton[@name="LOG IN"]';
  const selectorAndroid = 'android=new UiSelector().text("LOG IN")';
  return $(driver.isAndroid ? selectorAndroid : selectorIOS);
}
```

**Sniff.** Two steps — both required, in order:

1. Find copy-matching selectors on `+` lines: `@name="…"`, `@text="…"`, `@label="…"`, `contains(@text,…)`, `contains(@name,…)`, `UiSelector().text(…)` / `.textContains(…)` / `.description(…)`, `-ios predicate string:label == "…"`, or a `~`-prefixed value that is human copy (`~Sign Up`, `~LOG IN`) rather than an id (`~login_submit_button`).
2. **Check whether an id exists for that control on that platform** before flagging — don't guess. Grep the app source when it's available (`.accessibilityIdentifier` / `.appAccessibility(id:)` in Swift, `Modifier.testTag(` in Compose), grep `test/helpers/selectors.ts` and sibling page objects for an existing `~id` on the same screen, or check whether the *other* platform branch of the same getter already uses one. An id on the iOS branch and copy on the Android branch is the strongest signal.

**Fix.** Target the id: `$('~login_submit_button')`, or `$(platformLocator('~login_submit_button', '~login_submit_button'))` when both platforms expose it. Human copy in a selector is a *value*, not an identifier — if the test genuinely needs to verify the copy, assert on the text of an id-located element instead of locating *by* the text:

```typescript
private get buttonLogin() { return $("~login_submit_button"); }
// …and in the spec, assert the copy separately:
expect(await LoginPage.buttonLogin.getText()).toEqual("LOG IN");
```

**Do NOT flag** the case below — that's the P2 rule, one comment not two.

---

## P2 — Text-dependent selector where no id exists yet (must carry a tracked TODO)

Same coupling, different situation: the control genuinely has **no** `testTag` / `accessibilityIdentifier` on that platform, so text is the most stable handle currently available. That's the norm on this project's Android Jetpack Compose screens. It's still debt — it just isn't a defect the test author can fix alone.

**Sniff.** A copy-matching selector (patterns above) on a control that has no id on that platform, **and** no `// TODO(<TICKET>)` note next to it.

**Fix.** Two things, together:

1. Anchor on the most stable attribute actually available — prefer an identity attribute (`@resource-id`, `@content-desc`, `@password="true"`, a class + one distinguishing attribute) over copy; use copy only when nothing else distinguishes the element.
2. Leave the debt tracked: `// TODO(MOB-1417): replace with a Compose testTag once the app exposes one`.

**Do NOT flag** a text selector that already carries such a note (see `login.page.ts` `errorMessage`, `MOB-1417`) — that's documented, tracked debt, and re-flagging every occurrence buries the real findings. Keep the ticket alive instead.

Either way, when text is the only option, the reviewer's standing recommendation is: **ask the app team to add a Compose `testTag` / SwiftUI `accessibilityIdentifier`** — the one change that converts the largest number of fragile text selectors into stable `~id` ones. On the app side, that contract is enforced by [`../ios/accessibility-identifiers.md`](../ios/accessibility-identifiers.md) (MOB-1131) and [`../compose/accessibility.md`](../compose/accessibility.md).

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
