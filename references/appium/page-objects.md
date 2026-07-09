# Appium Page Objects — POM discipline

The Page Object Model only pays off if the boundaries hold: pages expose *actions and queries*, specs own *assertions and flow*, and selectors stay private. When that erodes, the suite rots into copy-pasted driver calls. Severity uses the orchestrator's taxonomy.

This project's convention (see `test/pageobjects/page.ts`): a base `Page` class with shared helpers, page subclasses with `private get` selectors and `public async` action methods, exported as singletons.

---

## P1 — Assertion inside a Page Object method

Page Objects model *what the screen can do*, not *what a test expects*. An `expect(...)` baked into a page method hides the check from the spec, makes the method single-purpose, and means a failure points at the page file instead of the test.

```typescript
// login.page.ts — wrong layer
public async login(u: string, p: string) {
  await (await this.btnSubmit).click();
  expect(await this.dashboard.isDisplayed()).toBe(true);   // assertion belongs in the spec
}
```

**Sniff.** `expect(` / `assert(` / `.should` on `+` lines in changed `*.page.ts` files.

**Fix.** Return state the spec can assert on (e.g. `isLoggedIn(): Promise<boolean>`, or return the next Page Object), and move the `expect` into the `it(...)`. A page method that *waits* for readiness is fine; one that *judges pass/fail* is not.

**Do NOT flag:** `assertNever(...)` (the `TypeGuards` exhaustiveness helper — it's a compile-time guard, not a test assertion), or an `expect`/`.should` that appears inside a comment. Match the real call, not the substring.

---

## P1 — Test data or business/flow logic embedded in a Page Object

Usernames, passwords, branching test scenarios, or "if logged in do X else Y" inside a page couples the page to a specific test and makes it unreusable.

```typescript
public async login() {
  await (await this.inputUsername).setValue("test@meapp.com");   // hardcoded test data in page
  await (await this.inputPassword).setValue("Passw0rd!");
}
```

**Sniff.** String literals that look like credentials/test data, or multi-branch scenario logic, on `+` lines in `*.page.ts`.

**Fix.** Parameterize the action (`login(username, password)`) and pass data from the spec or a fixtures/env source. Keep pages free of scenario decisions.

---

## P1 — Cross-test state leak via the page singleton

These pages are exported as singletons (`export default new LandingPage()`). Storing mutable per-test state on the instance leaks between specs and creates order-dependent flakiness.

**Sniff.** Instance fields on a Page Object that are written during actions (caches, "current user", counters) rather than being pure selector getters / stateless methods.

**Fix.** Keep Page Objects stateless — derive everything from the live screen. If state must be carried, own it in the spec or a test-context object, not the shared singleton.

---

## P2 — Driver / raw `$()` interaction in a spec file

Specs reaching past the Page Object to `$()`, `driver.*`, or raw selectors defeat the encapsulation and scatter locators into tests.

```typescript
// 01-landing.spec.ts
await $('~login_button').click();   // spec shouldn't know selectors
```

**Sniff.** `$(`, `$$(`, or a selector literal (XPath / `UiSelector` / `-ios predicate`/`class chain`) on `+` lines in `*.spec.ts` files; also a `driver.action`/`driver.getWindowSize` pair building a gesture, or a `driver.waitUntil` polling a UI selector, inside a spec.

**Fix.** Add/extend a Page Object method (or a helper) and call that from the spec. Specs should read as a sequence of intent: `await LandingPage.clickLogin()`.

**Distinguish leaked locators from legitimate driver calls.** A spec building or querying a **selector** (`$('//XCUIElementTypePickerWheel')`, `$$('//android.widget.NumberPicker')`) is a POM breach — move it to a page. A **selectorless device primitive** — `await driver.back()`, `await driver.pause(...)`, `driver.isKeyboardShown()`, `browser.waitUntil(...)` on a page-object query — is control flow the spec may legitimately own; don't flag those as locator leaks (though re-rollable ones may hit `helpers-and-reuse.md`).

---

## P2 — Public selector / leaked locator

Selectors exposed as public fields/getters let callers bypass the page's action methods and depend on locator internals.

**Sniff.** `public get <selector>` / `public <selector> =` returning an element, in `*.page.ts`.

**Fix.** Make selectors `private`; expose only intent-level actions and queries.

---

## P2 — Duplicated action method across pages

The same interaction (wait-then-click, wait-then-setValue) reimplemented in every page should live on the base `Page`.

**Fix.** Use the base `Page` methods that already exist — `tapWhenReady` (waitForDisplayed → click), `waitForElement`, `scrollToElement`, `verifyErrorMessage` — instead of re-rolling the sequence. If a genuinely new shared interaction is needed, promote it to the base `Page` so every subclass gets it. See `helpers-and-reuse.md` for the full toolbox and the re-rolling rule.

---

## Nit — Inconsistent naming / missing return type

Action methods without explicit `Promise<…>` return types, or naming that drifts from the project's `clickX` / `inputX` / `btnX` conventions.

**Fix.** Match the established naming and annotate async return types for clarity and tooling.

---

*This doctrine — selectors centralized as `get`-prefixed getters, page objects holding **no**
assertions and **no** per-test state, pages exported as already-instantiated singletons, and specs
kept free of selectors — is the [official WebdriverIO Page Object pattern](https://webdriver.io/docs/pageobjects/).
These rules enforce it against this project's `test/pageobjects/` layout (base `Page` + `private get`
selectors + `public async` actions).*
