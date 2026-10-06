# Appium E2E Structure — the feature-sliced `e2e/` tree (meAppTest MOB-2841 refactor)

meAppTest is moving from the type-first `test/{specs,pageobjects,helpers,data}` tree to a feature-sliced
`e2e/` tree. Every file in `e2e/` has exactly **one role**, its name says which, and its folder says who
owns it. A PR that puts a file in the wrong folder, gives it a second role, or re-writes something a
shared home already does undoes the refactor one file at a time — and no test run will catch it.
Severity uses the orchestrator's taxonomy.

**Fires on:** changed files under `e2e/` and `tests-unit/`, plus the one `test/` rule below. It does not
apply to an untouched line, and it does not apply to `test/` code except where stated — `test/` is the
old tree and is brought into line screen by screen as it migrates, never by a sweep.

**Read these first — they outrank every rule here, and they change while the migration is live:**

| Doc | What it decides |
|---|---|
| `docs/automation-architecture.md` §15.1 / §15.2 | What each file holds, the two layouts, the comment rule |
| `docs/CODE-CONVENTIONS.md` | File names, comments, where types and constants go, module shape |
| `docs/E2E-API-AND-HOOKS.md` | Test accounts, the API layer, seeding, expected values |
| `.claude/skills/review-changes/SKILL.md` | The **shared homes** table and the naming table — read it live, it grows with each migrated screen |
| `docs/plans/2026-09-23-refactor-e2e-spec-describe-taxonomy-plan.md` | The `describe` skeleton and the `testCaseFor` wrapper |

If a doc and this file disagree, follow the doc and say so in the finding. If the repo has no `e2e/`
directory, skip this whole file.

**Scope boundaries — don't double-flag:**

| Concern | Owned by |
|---|---|
| Placement / duplication in the **old `test/` tree** | [code-organization.md](code-organization.md) |
| Re-implementing a helper in the **old `test/` tree** | [helpers-and-reuse.md](helpers-and-reuse.md) |
| Test-id drift, `it` title shape, Allure severity values | [test-naming-and-metadata.md](test-naming-and-metadata.md) |
| Whether a type should exist at all | [../code-standards/typescript.md](../code-standards/typescript.md) |

For a file under `e2e/`, when one of those and a rule here fire at the same `file:line`, **keep the
finding from this file** — it names the exact destination the repo's own docs prescribe.

---

## The layout (what goes where)

```text
e2e/
  core/
    config/        Environment.ts, Timeouts.ts       — tiers and settings the suite reads at runtime
    hooks/         account.hooks.ts, sweep.hooks.ts  — hooks any feature can use
    types/         <topic>.ts                         — types the core exports
  features/<feature>[/<sub-feature>]/
    specs/         <feature>.spec.ts
    pageobjects/   <screen>.page.ts
    data/          <feature>.data.ts, <feature>.lang.ts
    hooks/         <name>.hooks.ts                    — hooks tied to this feature
  shared/
    api/           Api, area classes (EntryApi…), api/helpers/, api/accounts/, types.ts
    components/    widgets used on 2+ screens (input/Keyboard, nav/TabBar)
    helpers/       one concern each (WaitHelper, Cleanup, WeightMath, TestCase…)
    data/          values shared across features (WeightHistory, HttpStatus…)
    types/         exported shared types (platform.ts, element.ts…)
    page.ts        the base Page
tests-unit/        <kebab-topic>.test.ts
```

| The thing being added | Its home |
|---|---|
| A spec | `features/<feature>/specs/` |
| A page object | `features/<feature>/pageobjects/` of the feature that **owns the screen** |
| Inputs, seeds, expected copy | `features/<feature>/data/` (one feature) · `shared/data/` (2+ features) |
| A hook for one feature | `features/<feature>/hooks/` · used by any feature → `core/hooks/` |
| Anything that calls the backend | `shared/api/` (request bodies and parsing → `shared/api/helpers/`) |
| A timeout or setting many modules use | `core/config/` (`TIMEOUTS`) |
| A widget on more than one screen | `shared/components/` |
| An exported type | a types file — `shared/types/<topic>.ts`, `core/types/<topic>.ts`, or an area's `types.ts` |

---

## P2 — New work added to the old `test/` tree for a screen that already lives in `e2e/`

Once a screen has migrated, `test/` is frozen for it. A new case or page method added to the old copy
is code that has to be migrated a second time — or is silently lost when `test/` is deleted.

```typescript
// test/specs/dashboard.spec.ts — new case, but e2e/features/dashboard/specs/dashboard.spec.ts exists
it("MA-T5201 — should show the goal chip …", async () => { … });
```

**Sniff.** For each `+` line in `test/specs/<x>.spec.ts` or `test/pageobjects/<x>.page.ts`, check whether
`e2e/features/**/<x>.spec.ts` or `e2e/features/**/<x>.page.ts` exists on the PR's head.

**Fix.** Add the case to the `e2e/` spec and page instead. **Do NOT flag** a fix that keeps an existing
`test/` case running (a selector or wait fix on a modified line), a screen with no `e2e/` counterpart yet,
or a PR whose title says it is migrating that screen.

---

## P2 — File in the wrong folder

The folder says who owns the file. A page object under `shared/` because two features import it, or a
backend call inside a page object, puts the code where the next author will not look and will re-write it.

```typescript
// e2e/features/history/pageobjects/history.page.ts — a backend call in a page
public async seedEntries(email: string) { await Api.as(creds).entry.create(…); }

// e2e/features/settings/pageobjects/settings.page.ts — imports another feature's page
import DashboardPage from "@features/dashboard/pageobjects/dashboard.page";
```

**Sniff.** For each added or moved file under `e2e/`, compare its path against the layout table above.
Also: `Api\.|ApiClient|fetch\(` in a `*.page.ts` or `*.spec.ts` outside an arrange step;
`from "@features/<other>/pageobjects/` inside a `pageobjects/` file of a different feature; a `*.hooks.ts`
under `features/` imported by 2+ features.

**Fix.** Name the destination from the table: backend calls → `shared/api/`; cross-feature navigation →
`NavigationHelper` or a shared component, not one page importing another; a hook used by 2+ features →
`core/hooks/`. **Do NOT flag** a page object that stays with its owning feature even though other features
import it — ownership follows the screen, not the importer count.

---

## P2 — File holds something outside its role

Each file kind has one job (architecture §15.1). Anything else in it moves to the file that owns it.

| File | Must not hold |
|---|---|
| `*.spec.ts` | locators (`$(`, `$$(`), literal test values, direct API calls used for assertions |
| `*.page.ts` | API calls, assertions (other than `assertOnScreen`), test values |
| `*.data.ts` / `*.lang.ts` | functions or computed values — the math lives in a helper |
| `types.ts`, `types/*.ts` | any runtime code |
| `*.hooks.ts` | non-hook utilities, types, constants (values come from `data/` or `core/config/`) |
| class file | a second class, an exported function, a type, a module-level constant or function |

```typescript
// dashboard.data.ts — a computed value in a data file
export const DashboardData = {
  expectedAverage: (w: number[]) => w.reduce((a, b) => a + b) / w.length,
};
```

**Sniff.** `export (function|const \w+ = \()` or `=>` inside `*.data.ts`/`*.lang.ts`; `\$\(|\$\$\(` in
`*.spec.ts`; `expect\(` in `*.page.ts` other than `assertOnScreen`; `^(export )?(type|interface) ` in a
class file; `^(export )?(function|const) ` at module level in a file that also declares a `class`.

**Fix.** Move it to its owner: a calculation → `shared/helpers/` (e.g. `WeightMath.averageLb`); a type →
the types file; a module-level constant in a class file → `private static readonly` inside the class.

---

## Nit — Class-file / function-file order broken

Every file is a class file or a function file, in a fixed order, so a reader finds the same thing in the
same place. Order-only breaks are a Nit; a *mix* of the two layouts is the P2 above.

- **Class file:** imports → the class → `export default new …()`. Inside: `private static readonly`
  constants → fields → constructor → getters (locators) → public methods → private methods.
- **Function file:** imports → constants / module state → exported functions → private functions.

**Sniff.** A public method below a private one; a getter below a public method; a private function above
an exported one. **Fix.** Move the member to its slot. At most one order finding per file.

---

## P2 — File or symbol name breaks the convention

| Kind | Convention | Example |
|---|---|---|
| Class / helper / component file | `PascalCase.ts`, same name as its class | `EntryApi.ts`, `WaitHelper.ts` |
| Page / spec / hooks file | lowercase `<screen>.page.ts`, `<feature>.spec.ts`, `<name>.hooks.ts` | `dashboard.page.ts` |
| Data / copy file | `<kebab-name>.data.ts` / `.lang.ts` | `weight-history.data.ts` |
| Data / copy object | PascalCase, named after the file | `DashboardData`, `ScanDialogLang` |
| Unit test | `tests-unit/<kebab-topic>.test.ts` | `wait-helper.test.ts` |
| Options type | `…Options`, never `…Opts` | `RequestOptions` |
| Config keys | `UPPER_SNAKE` | `TIMEOUTS.ACCOUNT_SETUP` |

Method names are a promise: `is…`/`has…` only read; `…Now` reads once with no wait; `waitFor…` waits;
`read…`/`get…` return a value; `open…`/`tap…` act. One verb per job across siblings.

**Sniff.** New file names under `e2e/` that are kebab-case without a role suffix (`wait-helper.ts`), or
camelCase (`goalSettings.page.ts`); a data object not named after its file; `Opts\b`; an `is…` method
that taps or navigates. **Fix.** Give the rename. On macOS, rename through a temp name
(`git mv a.ts __tmp.ts && git mv __tmp.ts A.ts`) or git keeps the old casing.

---

## P2 — Relative import, or an `e2e/` file importing from `test/`

`e2e/` imports through `@core/*`, `@shared/*`, `@features/*`. A `../../..` path breaks the next time a
folder moves; an import from `test/` ties the new tree to the one being deleted.

```typescript
import { TIMEOUTS } from "../../../core/config/Timeouts";          // ❌
import { isAndroid } from "../../../../test/helpers/PlatformHelper"; // ❌ old tree
import { TIMEOUTS } from "@core/config/Timeouts";                   // ✅
```

**Sniff.** `from "\.\./\.\.` or `from ".*test/(helpers|pageobjects|data)` on `+` lines under `e2e/`.
**Fix.** Use the alias; if an `e2e/` copy of the imported code exists, import that instead.
**Do NOT flag** an import of a `test/` page whose screen has **no `e2e/` counterpart yet** — that is the
migration's known bridge (e.g. `landing.spec.ts` → `test/pageobjects/signup.page` until Signup migrates).
Flag it once the `e2e/` counterpart exists.

---

## P2 — Re-writes something a shared home already does

The shared-homes table in `.claude/skills/review-changes/SKILL.md` lists the one place each concern lives.
Re-writing it is the copy that misses the next fix. Read the table live; the common ones are:

| Hand-written in the diff | Use instead |
|---|---|
| `.then(() => true, () => false)` around a wait | `waitUntilOrFalse` (`@shared/helpers/WaitHelper`) |
| a hand-rolled `Promise.race` timeout | `withTimeout` / `isTimeoutError` (`WaitHelper`) |
| `driver.hideKeyboard()` in a page | `Keyboard` (`@shared/components/input/Keyboard`) |
| `e instanceof Error ? e.message : String(e)` | `describeError` (`@shared/helpers/Cleanup`) |
| `.catch(() => {})` | `cleanupFailed` / `setupFailed` / `bestEffort(why)` (`Cleanup`) |
| a bare ms number | `TIMEOUTS.<TIER>` (`@core/config/Timeouts`) |
| a bare HTTP status (`404`) | `HttpStatus` (`@shared/data/http-status.data`) |
| `driver.isAndroid` | `isAndroid` / `platformLocator` (`PlatformHelper`) |

**Sniff.** grep `+` lines for each left-column pattern, then confirm the shared symbol exists on the
PR's head before naming it. **Fix.** Name the exact symbol and import path.

---

## P2 — Spec doesn't use `testCaseFor` or the `describe` skeleton

Migrated specs build each test from its Zephyr id with `testCaseFor`, so the id is written once and the
Allure tag can't be forgotten. `describe` follows one shape so a missing group is visible:
`Screen > [account group] > Component > UI | State`, with `Navigation` for back-swipe.

```typescript
// ❌ old shape inside e2e/
it("MA-T923 — should land on My Weight …", async () => { tagTest("MA-T923", DashboardData.feature, "normal"); … });

// ✅
const it = testCaseFor(DashboardData.feature);
it("MA-T923", "normal", "should land on My Weight after the first login …", async () => { … });
```

**Sniff.** In `e2e/**/*.spec.ts`: `tagTest(` or `addTestId(`; an `it("MA-T` title string; no
`testCaseFor(` import; a component `describe` whose children are not `UI` / `State`.
**Fix.** Show the one-line `testCaseFor` call. **Do NOT flag** `it.skip` traceability stubs.

---

## P1 — Test account handled outside the hooks

Accounts come from `useLoggedInAccount` / `useAccount`, once per `describe`. Sharing one across blocks
makes tests order-dependent; deleting it by hand races the hook's own teardown.

```typescript
const account = useLoggedInAccount({ kind: "temporary", feature: "history" });
describe("Export", () => { /* reuses the outer account — shared across describes */ });
await account().api.account.remove();   // ❌ the hook deletes it
```

**Sniff.** In `e2e/**/*.spec.ts`: `useLoggedInAccount(`/`useAccount(` called twice in one `describe`, or
an outer accessor read inside a sibling `describe`; `api.account.remove(`; a hard-coded email/password.
**Fix.** One hook call at the top of each `describe`; drop the manual delete. Signing up through the UI
outside a Signup spec is the same rule at **P2** (slow, not wrong).

---

## P1 — The API is used to assert what the screen shows

The API is for *arranging* data. Asserting through it checks the backend, not the app — the test
passes while the screen shows the wrong value.

```typescript
expect((await account().api.entry.list())[0].weightLb).toBe(178.4);  // ❌ reads the server
expect(await DashboardPage.readLatestWeight()).toBe(latestLb(DashboardData.seededWeights)); // ✅
```

**Sniff.** `expect(` whose argument contains `api.` or an `Api` area call in an `e2e/` spec.
**Fix.** Read the value from the page object. **Do NOT flag** a spec whose subject *is* the API
(an API-only check named as such).

---

## P2 — Expected value or seed typed into the spec

Seeds belong in the feature's data file; expected numbers are derived with `WeightMath`, so a seed change
can't leave a stale constant behind.

**Sniff.** A numeric or copy literal inside `expect(…)` or a `seed:` block in an `e2e/` spec.
**Fix.** Move the value to `features/<feature>/data/<feature>.data.ts` (or `.lang.ts` for copy) and derive
the expectation: `latestLb(...)`, `rollingWeekAverageLb(...)`.

---

## P2 — Export nobody else uses

Export only what another file uses. Copying a module from `test/` carries its whole export surface
across — `OcrHelper` arrived with five exports nothing called.

**Sniff.** For each added `export`, grep `e2e/`, `tests-unit/`, `config/`, `tools/` for an importer.
Zero importers → finding (**medium** confidence unless the grep was run on the PR head).
**Fix.** Drop the `export` or delete the symbol. **Do NOT flag** a type in an exported function's
signature, or an API area method kept on purpose for future tests.

---

## Nit — Comment longer than one line, or history in a comment

`e2e/` comments are one line: a doc line on every exported member and public page method, saying its
contract or why it exists. No ticket trails, no "was / changed from". A measured device fact goes in a
doc (`docs/ocr-measurements.md`) and the code cites it in one line.

**Sniff.** A `//` run of 2+ lines or a multi-line `/** */` block on `+` lines under `e2e/`; a comment
with `MOB-\d+` that is not an expected-red citation; an exported symbol with no doc line.
**Fix.** Cut to one line; move the rest to the commit message or a doc.
**Do NOT flag** the expected-red `// MOB-XXXX` citation, or the one-line reason for a selector shaped
by a live trap. For `e2e/` files this rule replaces `code-organization.md`'s "however long" carve-out.
At most one comment finding per file.
