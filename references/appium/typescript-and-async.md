# Appium TypeScript & Async — the await trap and type safety

WebdriverIO commands are all asynchronous. A single missing `await` is the most common and most confusing bug in WDIO+TypeScript suites: the command may not run, or runs out of order, and the failure surfaces somewhere unrelated. This file also covers type-safety erosion (`any`, unsafe casts). Severity uses the orchestrator's taxonomy. `tsconfig` here has `strict: true` — keep it honest.

---

## P0 — Missing `await` on a WebdriverIO command (floating promise)

Every interaction (`$`, `click`, `setValue`, `waitForDisplayed`, `isDisplayed`, `getText`, …) returns a promise. Without `await`, the action is fire-and-forget: it may execute after later lines, or never, and assertions read stale/undefined state. With chained element access, a missing inner `await` returns a promise where an element is expected.

```typescript
public async clickLogin() {
  this.waitForElement(await this.buttonLogin);   // missing await — proceeds before element is ready
  (await this.buttonLogin).click();              // missing await — click may not happen
}

const text = el.getText();                       // text is a Promise<string>, not a string
expect(text).toBe("LOG IN");                     // always fails / misleads
```

**Sniff.** Calls to WDIO commands / Page Object async methods on a statement line with no leading `await` (and not deliberately stored as a promise to await later): `.click()`, `.setValue()`, `.addValue()`, `.waitForDisplayed()`, `.waitForExist()`, `.isDisplayed()`, `.getText()`, `.getAttribute(`, `$(`, `$$(`, and calls to project page methods (`LandingPage.clickLogin()`, `this.waitForElement(`). Also flag `expect(<unawaited promise>)`.

**Fix.** `await` every command and async helper. Enable `@typescript-eslint/no-floating-promises` + `await-thenable` and `eslint-plugin-wdio` so CI catches these mechanically. Reserve P0 for cases that change behavior (skipped action, assertion on a promise); a harmless missing await on a final fire-and-forget line is **P1**.

---

## P1 — `any` type defeating strict mode

`any` silences the compiler exactly where mobile automation is most error-prone (element handles, command results). The config already shows the smell.

```typescript
// wdio.shared.conf.ts
afterTest: async function (test: any, context: any, { error }: any) { … }
await (global as any).browser.takeScreenshot();
```

**Sniff.** `: any`, `as any`, `<any>`, or untyped params in changed `.ts` files.

**Fix.** Use WDIO's provided types: `import type { Frameworks } from "@wdio/types"` for hook params, and the global `browser`/`driver`/`$` from `@wdio/globals` (already in `tsconfig` `types`) instead of `(global as any).browser`.

---

## P1 — `(global as any).browser` / `(global as any).driver`

Reaching through `global` casts away types and bypasses the typed globals the project already configures.

**Sniff.** `(global as any).browser`, `global.driver`, `globalThis as any`.

**Fix.** `import { browser, driver, $ } from "@wdio/globals"` (or rely on the ambient `@wdio/globals/types`) and use them directly.

---

## P2 — Unhandled promise / no error context at boundaries

`async` helpers that can reject without any contextual error make failures hard to diagnose ("element not found" with no clue which screen).

**Fix.** Let WDIO's descriptive errors surface (don't wrap-and-rethrow blandly); where you do wrap, include the page/action in the message.

---

## P2 — Unsafe non-null assertion / cast

`el!`, `value as string` on values that can legitimately be null/undefined (e.g. `getAttribute` can return null) hides real "element missing / attribute absent" cases.

**Sniff.** `!.` non-null assertions and `as <type>` casts on command results in changed files.

**Fix.** Handle the nullable case explicitly (assert presence first, or branch), don't assert it away.

---

## Nit — Import / module hygiene

`require(...)` in TS files, unused imports, or deep relative paths where a barrel exists.

**Fix.** Use ES imports, remove unused symbols, follow the project's import conventions.
