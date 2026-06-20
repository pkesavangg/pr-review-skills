# Appium Test Structure & Assertions — a test that can't fail isn't a test

A senior reviewer's first question on any spec: *"Under what condition does this test fail?"* If the answer is "only if the app crashes", it's not protecting anything. This file covers assertion presence, test independence, and Mocha/Allure hygiene. Severity uses the orchestrator's taxonomy.

---

## P1 — Test with no meaningful assertion

A spec that performs actions but never asserts an outcome passes as long as nothing throws — it gives false confidence. The placeholder pattern below is the canonical offender.

```typescript
it("TC001 - should login with valid credentials", async () => {
  addTestId("TC001");
  await LandingPage.clickLogin();
  // assertion commented out — test passes even if login is broken
  // const secureArea = await $('...'); await expect(secureArea).toBeDisplayed();
});
```

**Sniff.** An `it(...)` body in changed specs with zero `expect(` / `assert` / `.should`, OR whose only assertion is commented out.

**Fix.** Assert the observable post-condition: `await expect(DashboardPage.header).toBeDisplayed()`. If the screen needed to assert doesn't exist yet, mark the test `it.skip` with a `// TODO: assert post-login state (<TICKET>)` rather than merging a no-op green test.

**Note for re-review:** placeholder tests are frequently "fixed" by a later commit adding the assertion — verify the assertion actually targets a real post-condition, not `expect(true).toBe(true)`.

---

## P1 — Order-dependent / shared-state test

Tests must pass in isolation and in any order. A test that only works because a previous test left the app on a certain screen is a latent flake (and breaks under `--shard`, retries, or `.only`).

**Sniff.** A spec that starts mid-flow with no `before`/`beforeEach` to establish state, or relies on a global/module variable mutated by an earlier `it`.

**Fix.** Reset to a known state in `beforeEach` (relaunch/reset the app or navigate home). Each `it` should set up its own preconditions.

---

## P1 — Empty or stub lifecycle hook merged

`before(() => {})` and friends left empty are dead code that signals unfinished setup — and often *hides* the missing reset that would prevent order-dependence.

```typescript
describe("Landing", () => {
  before(() => {});   // empty — either implement reset/launch or remove
```

**Sniff.** `before(`, `beforeEach(`, `after(`, `afterEach(` with an empty or no-op body on `+` lines.

**Fix.** Implement the needed setup/teardown (app launch, reset, login) or delete the empty hook.

---

## P2 — Commented-out test logic / assertions shipped

Large blocks of commented placeholder code (`// const secureArea = …`) merged into the suite are noise that masks whether the test is real.

**Sniff.** Commented-out `expect`/selector/action lines inside `it` bodies on `+` lines.

**Fix.** Either activate the code or remove it before merge.

---

## P2 — Inconsistent / missing Allure & test-id metadata

This project tags tests for reporting (`addTestId`, `addFeature`, `addSeverity`, `addLabel`). Mixing conventions (`addFeature("Login")` in one test, `addLabel("feature","Login")` in another) fragments the report.

```typescript
addFeature("Login");                 // TC001
addLabel("feature", "Login");        // TC002 — different API, same intent
```

**Sniff.** Divergent Allure annotation styles across changed specs, or new tests missing `addTestId`/severity entirely.

**Fix.** Standardize on one set of annotations per the project's pattern; ensure every test has a stable id and severity.

---

## P2 — Vague test name not describing behavior

`it("works")` / `it("test login")` don't say what's verified. Names should read as a behavioral spec.

**Fix.** `it("TC0NN - logs in with valid credentials and lands on the dashboard")` — id + observable behavior.

---

## Nit — Inline magic test data

Repeated literal usernames/emails/numbers inside specs.

**Fix.** Lift to named constants or a fixtures module so intent is clear and data is reusable.
