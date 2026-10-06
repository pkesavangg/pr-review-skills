# Appium Code Organization — declare it once, and declare it where the project keeps that kind of thing

A test suite rots along two axes that no runtime rule catches: the same code appears in two places, and a
declaration lands somewhere nobody will look for it. Both are invisible in a green run and both compound —
the second copy of a block is the one that misses the next fix, and a type declared inside a spec is a type
the next spec re-invents. Severity uses the orchestrator's taxonomy.

This file is the **structure and placement** lens for §4a.6 / §4.6. It answers one question per finding:
*is this declared once, and is it declared where this project keeps that kind of thing?*

**Scope boundaries — these rules live elsewhere, don't double-flag:**

| Concern | Owned by |
|---|---|
| Re-implementing a helper that **already exists** (`tapWhenReady`, `AuthHelper.loginAs`, `GestureHelper`) | [helpers-and-reuse.md](helpers-and-reuse.md) |
| The same **action method** duplicated across two page objects | [page-objects.md](page-objects.md) |
| Commented-out **test logic / assertions** shipped | [test-structure-and-assertions.md](test-structure-and-assertions.md) |
| Whether a type should exist **at all** (repeated inline shape → declare a type) | [../code-standards/typescript.md](../code-standards/typescript.md) |
| Whether a helper's **name** says what it does | [test-naming-and-metadata.md](test-naming-and-metadata.md) |
| Anything in meAppTest's feature-sliced **`e2e/`** tree (placement, file roles, naming, shared homes, comments) | [e2e-structure.md](e2e-structure.md) — the placement map below is for the old `test/` tree |

This file covers what those don't: **new** duplication the PR itself introduces with no helper to reuse yet,
and **where** a declaration belongs once it exists. When `code-standards/typescript.md`'s repeated-inline-shape
rule and this file's placement rule fire at the same `file:line`, keep **this** finding (platform beats the
generic language lens) and let its fix name both the type *and* the file it belongs in.

---

## Read the repo's own standards before applying any of this

A mature suite documents its structure, and that document outranks every rule here. Before flagging placement
or duplication, read whichever of these the repo has and treat them as authoritative:

- `docs/TEST-STANDARDS.md` — how a test is written (layering, what a spec may contain)
- `docs/TEST-RELIABILITY-STANDARDS.md` — flake policy, `expectedRed`, retry rules
- `.claude/skills/coding-standards/SKILL.md` (or equivalent) — the static-analysis gate the repo actually runs
- `docs/adr/` — decisions already argued and settled (e.g. meAppTest ADR-0002: `driver.isAndroid` is banned outside `PlatformHelper`)
- `CLAUDE.md` — the module map and the hard-won gotchas

If one of them prescribes a different layout than the map below, **prefer the repo's** and say so in the finding.
If one of them documents a convention as *deliberate*, it is not a defect — meAppTest's `coding-standards` skill
lists conventions its own gate flags but that must **not** be "cleaned up" (`this.retries(0)`, `it.skip`
traceability stubs, `adb`/`xcrun` complexity hotspots). Never flag those.

---

## The placement map (meApp-tuned; adapt to the repo's own layout)

| What is being declared | Where it belongs | Real anchor in this suite |
|---|---|---|
| A string-union / enum a page object's methods take or return | the page's own `*.page.ts` while exactly one page uses it; a `<module>.types.ts` **sibling** the moment a second page or a spec needs it | `test/pageobjects/signup.types.ts` (`WeightUnit`, `BioSex`, `DeviceKind`) |
| A shape describing test data — a fixture row, an expected record | the matching `test/data/<screen>.data.ts`, beside the data it types | `IntegrationRow` in `settings-integrations.data.ts`, `StableWeightEntry` in `stable-account.data.ts` |
| A type derived from a `const` table | next to that table, as `(typeof X)[keyof typeof X]` — never hand-retyped | `IntegrationSlug`, `ExportToastOutcome`, `SettingsSection` |
| A helper's options / result / verdict type | the helper's **own** file, exported alongside the function | `ViewportVerdict` + `PollOptions` (`ViewportHelper.ts`), `ReadOverlayOptions` (`OcrHelper.ts`) |
| An ambient declaration / module augmentation | `test/types/*.d.ts` — and **only** that; `test/types/` is not a dumping ground for domain types | `test/types/expect-message.d.ts` |
| A value two or more specs need | `test/data/` (data, copy, tables) or `test/helpers/` (behaviour, timing) — never re-declared in the second spec | `test/data/constants.ts` (`WAIT`), `test/helpers/timeouts.ts` (`TIMEOUTS`), `test/helpers/selectors.ts` |
| A locator | `test/helpers/selectors.ts` or a page-object getter — never a spec | — |
| **Anything type-shaped in a `*.spec.ts`** | nowhere. A spec imports types; it does not declare them | — |

---

## P2 — Type, interface, or enum declared inside a spec

A spec is a consumer, not a home. A type declared in `*.spec.ts` can't be imported without importing the spec
(which registers its `describe`/`it` as a side effect), so the next spec that needs the same shape re-declares it
— and the two copies drift the moment a field is added. It also hides the shape from the data fixtures that
should be validated against it.

```typescript
// switch-account.spec.ts — a profile shape declared mid-test
it("MA-T1234 — switching accounts keeps each profile intact", async () => {
  type Profile = { name: string; email: string; sex: string; height: string };
  const expected: Profile = { name: "Ann", email: "a@x.com", sex: "Female", height: "170" };
  // …
});
```

**Sniff.** On `+` lines in changed `test/specs/**/*.spec.ts`:
`rg -n '^\s*(export\s+)?(interface|enum|type)\s+[A-Z]' test/specs` — any hit is a finding. Also catch the
un-named form: the same inline object-literal annotation (`: { a: string; b: string }`) written twice in one spec.

**Fix.** Move the declaration to its home from the placement map — a data shape to the screen's `*.data.ts`
beside the fixtures it types, a page-object vocabulary union to the page or its `*.types.ts` sibling — export it,
and `import type { Profile } from "../data/switch-account.data"` in the spec. Where a fixture already exists,
type the fixture rather than the local: `satisfies Profile` at the data site catches drift at compile time in
*every* spec, not just this one. **Do NOT flag** a `typeof`-derived local alias used once for narrowing
(`type Row = (typeof ROWS)[number]`) where the source table is already imported — that adds no new vocabulary.

---

## P2 — A module's types split across more than one home

One module, one types home. When a union lives in `signup.types.ts` but a *second* file re-declares a near-copy
(a new `signup-types.ts`, a `types/signup.ts`, or the same literal union re-spelled inline in a method signature),
adding a value means finding all of them — which is exactly the drift that centralising them was meant to end.
The same applies to a helper whose options type gets re-declared at a call site instead of imported.

```typescript
// signup.types.ts (existing)
export type WeightUnit = "kg" | "lb" | "lbs/oz";

// signup-birthday.page.ts — re-spells the union instead of importing it
public async selectBirthWeightUnit(unit: "kg" | "lb" | "lbs/oz"): Promise<void> { … }

// signup.baby.types.ts (new in this PR) — a second home for the same vocabulary
export type BabyWeightUnit = "kg" | "lb" | "lbs/oz";
```

**Sniff.** For each type-ish name added in the diff, grep the suite for the same *value set* rather than the same
name: `rg -n '"kg"\s*\|\s*"lb"' test/` . Two hits in two files, or an inline union in a signature whose exact
members already exist as a named type, is the finding. Also flag a **new** `*.types.ts` / `types/*.ts` file added
for a module that already has one.

**Fix.** Keep the existing home, import from it, and delete the copy —
`import type { WeightUnit } from "./signup.types"`. If the second module genuinely needs a *wider or narrower*
set, express the relationship instead of restating it (`type BabyWeightUnit = Exclude<WeightUnit, "lbs/oz">`) so
one edit still propagates. **Do NOT flag** two same-named types in genuinely unrelated domains, or a `.d.ts`
ambient declaration coexisting with a domain type of the same name.

---

## P2 — New duplication: the same block copy-pasted instead of extracted

The second copy is the one that misses the next fix. This is the failure mode behind most "the suite passes on
Android but not iOS" reports: a settle, a retry, or a re-query got hardened in one copy and not the other. It is
also the one organization defect a quality gate measures directly — meAppTest's Sonar gate caps duplication on
**new** code at 3% and fails the build on any new violation, so a copy-paste in this PR blocks the merge whether
or not a reviewer notices.

```typescript
// history.spec.ts — and the identical eleven lines again in export-history.spec.ts
await HistoryPage.openExportSheet();
await HistoryPage.selectRange("last30");
await HistoryPage.tapExport();
const toast = await HistoryPage.readExportToast();
expect(toast).toContain(HistoryData.exportSuccess);
```

**Sniff.** Within the diff (not against the whole repo): a run of **6+ non-trivial lines** appearing twice, or a
string/number literal added **3+ times** across the changed files (Sonar `S1192`). `git diff` the added lines and
sort them — repeated blocks surface as adjacent identical runs. Two `describe` blocks whose `beforeEach` bodies
are line-for-line identical is the most common shape.

**Fix.** Extract to the layer that owns the concern, and name the destination:
a multi-step user journey → a page-object action method or a flow helper in `test/helpers/`;
a repeated arrange → a shared `beforeEach` or a seeder (`StableAccountSeeder`);
a repeated literal → a named export in the screen's `*.data.ts`.
Then call it from both sites. **Do NOT flag** two tests that *look* alike but assert different outcomes (the
duplication is incidental and collapsing them hides the cases), a repeated single-line `await Page.tap…()`,
or table-driven fixtures where repetition **is** the data.

---

## P2 — Shared thing left local when a second call site appears

The trigger is arithmetic, not taste: **one call site is local, two is shared.** A helper, constant, or type that
starts inside one spec is fine there until a second file needs it — at which point leaving it put forces the
second file to either import a spec (see the first rule) or copy the code. Reviewing a PR is the only moment this
is cheap to fix, because the PR *is* where the second call site appears.

```typescript
// weightless.spec.ts — a spec-local helper the new spec below also needs
async function seedThreeWeighIns(email: string) { … }

// weightless-history.spec.ts (new in this PR) — so it copies it
async function seedThreeWeighIns(email: string) { … }   // ← same body
```

**Sniff.** For each module-scope `function` / `const` added or touched in a changed `*.spec.ts`, grep the suite
for a second use or a second definition of the same shape. Also: a `const` block of ms values, a selector string,
or copy text added to a spec when `test/helpers/timeouts.ts`, `test/helpers/selectors.ts`, or the screen's
`*.data.ts` is its documented home.

**Fix.** Promote it once, to the layer named in the placement map — behaviour and timing to `test/helpers/`,
data and copy to `test/data/`, interaction to the page object — then import it in both specs and delete both
local copies. Say which file in the comment. **Do NOT flag** a genuinely single-use spec-local helper that only
exists to name a step for the reader, or a thin local wrapper that just delegates to an imported helper.

---

## P2 — Change contradicts a standard the repo documents

The strongest finding available is the project's own written rule, because there is nothing to argue: the standard
was already agreed. This rule exists so a documented convention with no matching generic rule still gets caught —
and it is the one rule here whose evidence must be a **doc citation with a line reference**, not a judgment.

```typescript
// settings-mykids.spec.ts — banned by ADR-0002 (driver.isAndroid outside PlatformHelper), lint level: error
const label = driver.isAndroid ? "Add a Baby" : "Add Baby";

// history.spec.ts — TEST-STANDARDS.md: no silent failures feeding an assertion
const shown = await HistoryPage.exportRow.isDisplayed().catch(() => false);
expect(shown).toBe(true);
```

**Sniff.** After reading the repo's standards docs (above), check each touched spec against the specific,
greppable rules they state. In meAppTest that is at minimum: no `driver.isAndroid` outside `PlatformHelper`
(ADR-0002, lint `error`); no raw `$` / `$$` in a spec (selectors live in page objects / `selectors.ts`);
no `.catch(() => false)` feeding an assertion (use `ElementHelper.isDisplayedNow` / `isDisplayedSafe`);
platform skips via `androidOnly(this, "why")` / `iosOnly(this, "why")` from `helpers/applicability.ts`, never a
bare `this.skip()`; a known-broken test wrapped in `expectedRed("MOB-XXXX", …)` rather than weakened.

**Fix.** Quote the standard and its location in the finding — *"`docs/TEST-STANDARDS.md` §3 bans `driver.isAndroid`
outside `PlatformHelper` (ADR-0002, lint level `error`); use `platformLocator(android, ios)`"* — then give the
one-line correction. **Do NOT flag** a pre-existing violation on an untouched line, and do not flag a convention
the repo explicitly documents as deliberate even though its own analyzer complains about it.

---

## Nit — Comment that restates the code or narrates the change

A comment earns its place by carrying something the code cannot: a measured device fact, a ticket, a reason a
non-obvious choice is correct. A comment that re-says the next line costs a reader attention and goes stale
silently — and worse, it *dilutes* the comments that matter, which in a mobile suite are the load-bearing ones
(overlay timings, WDA hangs, platform quirks). Diff narration is the same defect aimed at the wrong audience:
"changed from `waitForDisplayed`" belongs in the commit message, which is where a reader can see both versions.

```typescript
// Click the login button
await LoginPage.tapLogin();

// Wait for the dashboard
await DashboardPage.waitForDashboard();

// Changed this from 5000 to 8000 because it was flaky
await el.waitForDisplayed({ timeout: TIMEOUTS.MEDIUM });

// const oldSelector = '~login-btn';   // keeping this just in case
```

**Sniff.** On `+` comment lines in the diff, three shapes only:
(a) the comment's words are the next statement's identifiers rephrased (`// tap X` above `tapX()`);
(b) it narrates history or the diff — starts with `Changed`, `Updated`, `Added`, `Removed`, `Was`, `Previously`,
`Now we`, or names an old value;
(c) it is commented-out code with no ticket (for commented-out **test logic / assertions**, that's
`test-structure-and-assertions.md`'s P2 — flag it there, not here).

**Fix.** Delete (a) and (b) — move (b)'s content to the commit message or PR body if it's worth keeping — and
delete (c). Where a comment was reaching for a real reason, keep the reason and drop the narration:
`// 8s: the export toast surfaces ~1.9s after the tap on a tablet (MOB-2311)` says something the code cannot.

**Do NOT flag** — and this carve-out matters more than the rule, because this suite's long comments are the
expensive part of it:

- any comment recording a **measured** device/timing/platform fact, an OCR band, a WDA or Appium quirk, or a
  reason a workaround exists — however long
- a ticket reference, an `expectedRed` defect citation, a `TODO(MOB-xxxx)`, an ADR pointer, a link to a spec
- a required-argument-style reason (`bestEffort(…, "why")`) — that's an API contract, not a comment
- a doc comment (`/** … */`) on an exported helper, type, or page-object method, including its `@param`s
- a comment explaining why something Sonar/lint flags is nonetheless correct (`this.retries(0)`, a load-bearing cast)
- a header comment on a `*.types.ts` / `*.data.ts` file explaining what the module centralises and why

One comment finding per file at most, and never a comment finding on a file whose other findings are all clean —
if a comment is the only thing wrong with a file, it is not worth an author's attention.
