# Appium Test Naming & Metadata — the test's contract with the reader and the report

Every test carries two contracts beyond its assertions:

1. **Its names.** The `describe` title says *what surface is under test*; the `it` title says *what observable behaviour is verified*. When a run goes red at 2 a.m., these two strings are the entire diagnosis anyone gets before opening the file.
2. **Its metadata.** `addTestId` / `addFeature` / `addSeverity` / `addLabel("tms", …)` are what connect a passing green tick to a Zephyr test case and an Allure report section. Wrong metadata isn't cosmetic — it pushes a result onto the *wrong* test case.

This file owns naming and reporting metadata. Assertion presence, isolation, and lifecycle hooks live in [`test-structure-and-assertions.md`](test-structure-and-assertions.md); general TypeScript naming and enum/const discipline live in [`../code-standards/typescript.md`](../code-standards/typescript.md).

Severity uses the orchestrator's taxonomy. **Flag on `+`/modified lines only** — never sweep the existing suite. If a repo `CLAUDE.md` / `docs/TEST-STANDARDS.md` documents a different convention, prefer it and skip the conflicting rule.

---

## P1 — Invalid Allure severity value

`addSeverity()` takes a **fixed vocabulary**. Allure recognizes exactly five levels — `blocker`, `critical`, `normal`, `minor`, `trivial` (the `Severity` enum in `allure-js-commons`). Anything else is accepted at compile time as a plain string and then silently misfiles the test in the report: the severity widget shows a value it can't rank, and severity-based filtering (`--severity critical`) misses the test entirely.

`"high"`, `"medium"`, and `"low"` are the common inventions — they look right, they're wrong, and nothing tells you.

```typescript
addSeverity("high");     // not an Allure severity — silently unranked in the report
addSeverity("medium");   // same
addSeverity("low");      // same
```

**Sniff.** `addSeverity("<value>")` on `+` lines where `<value>` is not one of `blocker` / `critical` / `normal` / `minor` / `trivial`.

**Fix.** Map the intent onto a real level and — better — stop passing a raw string at all. The enum makes the invalid value a compile error instead of a silent report defect:

```typescript
import { Severity } from "allure-js-commons";

addSeverity(Severity.CRITICAL);   // was "high"
addSeverity(Severity.NORMAL);     // was "medium"
addSeverity(Severity.MINOR);      // was "low"
```

| Intended | Use |
|---|---|
| blocks the release / core flow broken | `Severity.BLOCKER` |
| high — major feature broken | `Severity.CRITICAL` |
| medium — default for a normal case | `Severity.NORMAL` |
| low — cosmetic, edge case | `Severity.MINOR` |
| trivial — typo-level | `Severity.TRIVIAL` |

Because the existing suite has a large number of legacy `"high"`/`"medium"`/`"low"` calls, **flag only the ones this change adds or edits**, and mention the bulk migration once in the summary rather than per-line.

---

## P1 — Test-case ID drift between the title, `addTestId`, and the `tms` label

The same test case ID is written three times per test — in the `it` title, in `addTestId(...)`, and in `addLabel("tms", ...)` — and each is consumed by a *different* system:

- the **`it` title prefix** is what the `afterTest` hook parses to write `zephyr-testcases/results/<MA-T id>.result.json`, which `zephyr-update.sh` pushes to the Zephyr cycle;
- **`addTestId`** is the Allure test-case link;
- **`addLabel("tms", …)`** is the Allure→TMS integration link.

Copy-pasting a test block and updating two of the three is the classic failure. The run then reports **the wrong test case as passing** — worse than a red, because it manufactures false confidence in a case that never ran.

```typescript
it("MA-T1616 — tapping NEXT skips Permission Settings and opens the pairing screen", async function () {
  addTestId("MA-T1616");
  addLabel("tms", "MA-T1622");   // ← drifted: this result lands on MA-T1622
});
```

**Sniff.** Within one `it` block on `+` lines, extract the `MA-T\d+` (or the repo's `[A-Z]{2,}-T?\d+`) from the title prefix, from `addTestId("…")`, and from `addLabel("tms", "…")`. Flag when they are not all identical. Also flag an `addTestId` whose ID appears in a *different* `it` in the same file (a copy-paste that was never updated).

**Fix.** Make all three the same ID. When a single `it` genuinely covers two Zephyr cases, that must be explicit — two `addLabel("tms", …)` calls with both IDs, and both IDs in the title — not one ID in the title and the other in the label.

**Do NOT flag** a deliberately paired title (`MA-T2770 / MA-T2814 — …`) where both IDs also appear in the metadata; that's the documented multi-case form.

---

## P1 — New test with no test-case ID or metadata block

A test with no ID exists only inside the repo. It never appears in the Allure feature tree, and — because the `afterTest` hook keys on the `MA-T####` prefix in the title — it never writes a Zephyr result, so the run reports coverage it didn't push. It's also unfindable: the standing rule is *grep `test/specs` for the MA-T id before writing a case*, and an unlabelled test can't be found that way, so it gets written twice.

```typescript
it("shows the empty state", async function () {   // no ID, no metadata — invisible to Allure and Zephyr
  await SettingsPage.openMyKids();
  expect(await SettingsPage.emptyState.isDisplayed()).toBe(true);
});
```

**Sniff.** A new `it(` on `+` lines whose title has no `MA-T\d+`-style prefix, or whose first statements contain no `addTestId(`.

**Fix.** Add the ID to the title *and* the metadata block. If the case has no Zephyr ID yet, create one before merging — or mark the test `it.skip` with a `// TODO(<TICKET>): needs an MA-T id` so it can't report a green it isn't entitled to.

---

## P2 — Repeated Allure metadata boilerplate — extract one call

Four annotation calls at the top of every test, with the ID typed twice and the feature string typed once per test, is ~4 lines × N tests of pure duplication. It has three concrete costs, all of which show up in this suite today:

- **Drift** — the ID appears in three places, so a copy-paste can (and does) desynchronize them.
- **Invalid values** — `addSeverity` takes an unchecked `string`, so `"high"` compiles.
- **Feature-string typos** — `addFeature("Settings Phase 2 — My Kids")` retyped per test fragments the Allure feature tree the moment one copy differs by a character or an em-dash.

```typescript
// repeated verbatim above every it() in the file
addTestId("MA-T4646");
addFeature("Settings Phase 2 — My Kids");
addSeverity("high");
addLabel("tms", "MA-T4646");
```

**Sniff.** ≥3 `it` blocks in the changed file that each open with the same `addFeature("…")` string plus an `addTestId`/`addLabel("tms", …)` pair, on `+` lines.

**Fix.** Collapse the four calls into one typed helper. The ID is passed once (so it *cannot* drift), the severity is an enum (so `"high"` can't compile), and the feature comes from a module constant (so it can't be retyped differently).

```typescript
// test/helpers/AllureMeta.ts
import { addTestId, addFeature, addSeverity, addLabel, addStory } from "@wdio/allure-reporter";
import { Severity } from "allure-js-commons";

export interface TestMeta {
  /** Zephyr / TMS case id — used for addTestId AND the tms label, so they can't diverge. */
  id: string;
  feature: string;
  severity?: Severity;
  story?: string;
  tags?: readonly string[];
}

export function testMeta({ id, feature, severity = Severity.NORMAL, story, tags = [] }: TestMeta): void {
  addTestId(id);
  addLabel("tms", id);
  addFeature(feature);
  addSeverity(severity);
  if (story) addStory(story);
  for (const tag of tags) addLabel("tag", tag);
}
```

```typescript
// test/data/features.ts — one source of truth for the feature tree
export const FEATURE = {
  MY_KIDS: "Settings Phase 2 — My Kids",
  ACCOUNT_SETTINGS: "Account Settings",
} as const;
```

```typescript
// the spec — one line, no repeated id, no free-text severity
it("MA-T4646 — Verify Save is disabled until every required field is filled", async function () {
  testMeta({ id: "MA-T4646", feature: FEATURE.MY_KIDS, severity: Severity.CRITICAL });
  …
});
```

Where a whole `describe` shares a feature, hoist it further — a small `describeFeature(FEATURE.MY_KIDS, () => …)` wrapper, or a `beforeEach` that applies the shared labels — so the per-test call carries only what actually varies.

**Do NOT flag** a single new test added to a file that already uses the four-call style; matching the file wins over a one-off refactor. Raise this when the change *introduces* the pattern at scale (a new spec, or several new tests), and name the helper to create.

---

## P2 — `it` title doesn't follow the suite's `<ID> — <observable behaviour>` shape

The `it` title is the failure message. It should be readable standalone, in a report, by someone who has never opened the file — which means: the case ID, then a statement of the behaviour being verified, in the format the rest of the suite uses. Mixed separators (`—` vs `:` vs `[…]` vs `-`) also break any tooling that parses the prefix, including the Zephyr result writer.

```typescript
it("MA-T971: save disabled", …)            // wrong separator, and "save disabled" isn't a behaviour
it("[MA-T515] Log Out is visible", …)       // bracket form — different from the file's other tests
it("works", …)                              // says nothing
it("test add baby", …)                      // names the action, not the expected outcome
```

**Sniff.** On `+` lines, an `it(` title that: uses a separator other than the file's dominant one after the ID (` — ` in this suite; also seen: `: `, ` - `, `[ID]`); or has fewer than ~4 words after the ID; or is a bare action with no expected outcome (`"tap save"`, `"open settings"`); or duplicates another `it` title in the same `describe`.

**Fix.** `<ID> — <verb phrase naming the observable result>`:

```typescript
it("MA-T971 — Verify Save button remains disabled when Name field is empty on Add Baby form", …)
it("MA-T798 — Verify Save button is disabled by default on Add Baby form", …)
```

The test should be reconstructable from the title alone. Keep the separator consistent with the file's other tests — do not introduce a fourth format.

---

## P2 — `describe` title doesn't name the screen or section under test

`describe` blocks build the report's tree and the mental model of the file. The suite's convention is `<Screen / Feature>` at the top level and `<Screen> — <section>` (or a nested plain `<section>`) inside — e.g. `"Settings Phase 2 — My Kids"` → `"Add Baby Form"` → `"Add Baby Form — Validation"`. A vague or file-shaped title (`"tests"`, `"spec"`, `"mykids.spec"`, `"misc"`) contributes nothing.

```typescript
describe("tests", () => { … });            // says nothing
describe("settings-mykids.spec.ts", …);     // names the file, not the surface
describe("Some checks", …);                 // no surface, no scope
```

**Sniff.** On `+` lines, a `describe(` title that: is a filename or contains `.spec`; is generic (`tests`, `suite`, `misc`, `checks`, `others`); is a single non-descriptive word; or names a *scenario* rather than a surface when it sits at the top level of a spec file.

**Fix.** Name the screen/feature, and let nesting carry the scope:

```typescript
describe("Settings Phase 2 — My Kids", () => {
  describe("Add Baby Form", () => {
    describe("Add Baby Form — Validation", () => { … });
  });
});
```

Also flag the inverse: a *new spec file* whose top-level `describe` duplicates a surface an existing spec already owns — the standing rule is one spec + one page object per screen module, so the tests belong in the existing `describe`, not a parallel file.

---

## P2 — Spec-local helper function name doesn't say what it does

Specs grow local helpers (`goBack`, `fillBabyName`, `fillBabyBirthday`) and those names are read far more than the bodies. A generic name (`helper`, `doIt`, `setup2`, `handle`) forces every reader into the body, and hides duplication — two functions named `helper1`/`helper2` in different specs may be the same flow, which is how [`helpers-and-reuse.md`](helpers-and-reuse.md) violations start.

**Sniff.** On `+` lines in `*.spec.ts` / `*.page.ts`: a function whose name is a bare noun, a generic verb (`handle`, `process`, `check`, `run`, `doX`), numbered (`step1`, `setup2`), or abbreviated past recognition; a boolean-returning helper without an `is`/`has`/`can`/`should` prefix; an assertion helper not prefixed `assert`/`verify`/`expect`.

**Fix.** Verb-first, naming the effect and the subject — `fillBabyBirthWeight()`, `assertScreen(landmark, "User Profile")`, `isOnMultiAccountScreen()`. Match the verb vocabulary the suite already uses: pages use `click…` / `input…` / `open…` / `select…`, helpers use `wait…` / `is…` / `verify…` / `assert…` / `navigate…`. Full naming table in [`../code-standards/typescript.md`](../code-standards/typescript.md).

**Then ask the reuse question.** If the helper is a well-named copy of something in `test/helpers/`, the naming fix isn't enough — that's the re-rolling rule in [`helpers-and-reuse.md`](helpers-and-reuse.md). Post one finding, not two.

---

## Nit — Metadata calls crammed onto one line or in inconsistent order

`addTestId("MA-T844"); addFeature("…"); addSeverity("normal");` on a single line, or a different call order per test, makes the block harder to scan and to diff — a changed severity shows as a whole-line change.

**Fix.** Prefer the single `testMeta({...})` call above. Where the four-call form stays, keep one call per line in a fixed order (`addTestId` → `addLabel("tms", …)` → `addFeature` → `addSeverity` → extra labels) so diffs stay surgical.
