# TypeScript Code Standards — using the language's own constructs

This is the *language-idiom* lens, separate from the framework lenses. The question it asks is not "does this work?" but **"is this expressed with the construct the language provides for it?"** — a set of allowed values should be a union/`enum`, a repeated object shape should be an `interface`/`type`, a never-reassigned binding should be `const`, a fixed table should be `as const`, and every name should say what the thing *is* or *does*.

These rules fire on **any** changed `.ts` file — Appium specs, page objects, helpers, config. They are deliberately cheap to check and cheap to fix; almost every one is a mechanical edit that removes a whole class of future bug.

Severity uses the orchestrator's taxonomy (`P0` / `P1` / `P2` / `Nit`). **Flag on `+`/modified lines only** — never sweep pre-existing code. If a repo `CLAUDE.md` / `README` / `eslint.config.*` documents a different convention, prefer it and skip the conflicting rule.

---

## P1 — Stringly-typed value where a union or `enum` should constrain it

A bare string literal passed to an API that accepts only a fixed set of values compiles no matter what you type. The typo, the invented value, and the renamed value all ship silently, and the failure shows up as *missing data in a report* or *a branch that never runs* — never as a compile error.

This is the single highest-yield rule in this file because the compiler can enforce it for free.

```typescript
// severity is a fixed vocabulary — but nothing here checks it
addSeverity("high");            // "high" is not a value the reporter understands
setUnitSystem("imperial");      // typo "imperail" would compile
const platform = "andriod";     // never equals "android" — branch silently dies
```

**Sniff.** On `+` lines: a string literal argument to a function whose parameter is a closed vocabulary (severity, status, platform, unit, environment, role, direction, mode), a string compared with `===` against 2+ hardcoded alternatives, or the same literal repeated in ≥3 places as a *value* (not copy).

**Fix.** Import the library's own enum when one exists; otherwise declare a union next to the code that owns it.

```typescript
// 1. the library already ships the vocabulary — use it
import { Severity } from "allure-js-commons";
addSeverity(Severity.CRITICAL);

// 2. no library enum — declare the union once
export const UNIT_SYSTEMS = ["imperial", "metric"] as const;
export type UnitSystem = (typeof UNIT_SYSTEMS)[number];
function setUnitSystem(unit: UnitSystem): Promise<void> { … }
setUnitSystem("imperail");   // ✗ compile error — caught before the run
```

Prefer a `const` object + derived union (`as const`) over a TypeScript `enum` in new code: it erases at compile time, keeps the runtime values as plain strings (so they still serialize/log readably), and gives the same exhaustiveness. Reach for a real `enum` only when the project already uses them or you need reverse mapping.

**Do NOT flag** a genuinely free-form string — a user-facing message, a test-data value, a selector literal, a file path, a log line.

---

## P1 — Non-exhaustive `switch` over a union with a silent `default`

Once a value is a union, the compiler can guarantee every case is handled — but only if the `switch` has no catch-all. A `default: return`/`break` throws that guarantee away: add a variant to the union and every switch keeps compiling while silently doing nothing for the new case.

```typescript
function label(unit: UnitSystem): string {
  switch (unit) {
    case "imperial": return "lb";
    case "metric":   return "kg";
    default:         return "";     // a new "stone" variant returns "" forever
  }
}
```

**Sniff.** A `switch` on a union/enum-typed value with a `default:` that returns a neutral value (`""`, `null`, `0`, `undefined`) or falls through silently, on `+` lines.

**Fix.** Drop the catch-all and let the compiler prove exhaustiveness, or make the default an explicit exhaustiveness assertion so a new variant becomes a *compile* error, not a runtime shrug. This project already ships the helper: `assertNever` in [`test/helpers/TypeGuards.ts`](../appium/helpers-and-reuse.md).

```typescript
import { assertNever } from "../helpers/TypeGuards";

function label(unit: UnitSystem): string {
  switch (unit) {
    case "imperial": return "lb";
    case "metric":   return "kg";
    default:         return assertNever(unit);   // ✗ compile error when a variant is added
  }
}
```

**Do NOT flag** a `switch` over an open-ended input (a raw server string, user text) where a real fallback is the correct behaviour — there the `default` is the feature, not the bug.

---

## P1 — Repeated inline object shape instead of a declared `interface` / `type`

The same object literal shape written out at three call sites is three places to update and three chances to disagree. It also blocks the compiler from catching a missing field, because each site independently defines what "complete" means.

```typescript
// three copies of one concept, none of them named
async function addBaby(data: { name: string; birthday: string; sex: string }) { … }
async function editBaby(data: { name: string; birthday: string; sex: string; id: string }) { … }
const fixture = { name: "Ava", birthday: "2024-01-02", sex: "Female" };   // is `id` needed? nothing says
```

**Sniff.** On `+` lines: the same (or near-same) inline object type annotation in ≥2 signatures, an exported function taking an unnamed multi-field object literal type, or a `data/*.data.ts` fixture with no declared type.

**Fix.** Name the concept once and reuse it. Use `interface` for an object contract others implement or extend, `type` for unions, intersections, mapped/derived shapes, and function types. Derive variants instead of re-typing them.

```typescript
export interface BabyProfile {
  name: string;
  birthday: string;
  sex: BiologicalSex;
}
export type BabyProfileUpdate = BabyProfile & { id: string };

async function addBaby(data: BabyProfile): Promise<void> { … }
async function editBaby(data: BabyProfileUpdate): Promise<void> { … }
const fixture: BabyProfile = { name: "Ava", birthday: "2024-01-02", sex: BiologicalSex.Female };
```

**Do NOT flag** a one-off options bag used at a single call site — naming that adds indirection without buying anything.

---

## P2 — Naming that doesn't say what the thing is or does

Names are the API of the file. The convention below is standard TypeScript and is what the rest of this suite follows; a name that breaks it costs every future reader a lookup.

| Kind | Convention | Good | Bad |
|---|---|---|---|
| Function / method | `camelCase`, **verb-first** — says what it *does* | `fillBabyName`, `waitForKeyboardHidden` | `babyName`, `handle`, `doIt`, `test1` |
| Function returning a boolean | `is` / `has` / `can` / `should` prefix | `isOnMultiAccountScreen`, `hasActiveSprint` | `multiAccount`, `checkScreen` |
| Function that asserts / throws | `assert` / `verify` / `expect` prefix | `assertScreen`, `verifyErrorMessage` | `screen`, `checkIt` |
| Variable / parameter | `camelCase` noun | `babyProfile`, `retryCount` | `data2`, `tmp`, `x`, `arr` |
| Type / interface / class / enum | `PascalCase` singular noun | `BabyProfile`, `UnitSystem` | `babyProfile`, `IBabyProfile`, `Datas` |
| Module-level constant | `UPPER_SNAKE_CASE` | `TIMEOUTS`, `BUTTON_NAV_UDIDS` | `timeouts_ms`, `Wait` |
| File | matches its dominant export's convention | `AuthHelper.ts`, `login.page.ts` | `helper2.ts`, `Login.Page.ts` |

An `async` function should be named for the *result*, not the mechanics: `getBabyProfile()`, not `fetchDataAsyncPromise()`.

**Sniff.** On `+` lines: a function whose name is a bare noun or a generic verb (`handle`, `process`, `doWork`, `check`, `run`, `helper`, `test1`, `fn`); a boolean-returning function without an `is`/`has`/`can`/`should` prefix; a `PascalCase` function or a `camelCase` type; an `I`-prefixed interface; single-letter or numbered identifiers (`d`, `x2`, `data2`) outside a short lambda; a module-level mutable-looking constant in `camelCase`.

**Fix.** Rename to the convention above and let the rename ripple — the compiler finds every call site. When the right name is long, take the long name: `waitForBioSexDialogToClose` beats `wait2`.

**Do NOT flag** conventional short names in tight scopes (`i`, `el`, `e`, `_`), names fixed by an external API you're implementing, or names the repo's own docs prescribe.

---

## P2 — `let` for a binding that is never reassigned

`const` is a fact the reader can rely on without scanning the rest of the function. `let` says "this changes" — when it doesn't, the reader pays for a promise the code never keeps, and a later edit can reassign it without anyone noticing.

```typescript
let selector = platformLocator("~appBarBack", "~chevronLeft");   // never reassigned
let pickers  = await $$('//android.widget.NumberPicker');        // never reassigned
```

**Sniff.** `let <name> =` on a `+` line with no later `<name> =`, `<name>++`, `<name> +=` in the same scope. `var` anywhere in a `.ts` file is always a finding.

**Fix.** `const`. For a genuinely accumulating value, keep `let` — but initialize it at the point of first knowledge rather than declaring it empty at the top of the function.

---

## P2 — Magic number / repeated literal with no named constant

A bare `4000` in a wait, a `15` in a picker index, a `0.85` in a gesture ratio — each is a decision nobody can review because the reason isn't written down, and each is invisible when the value needs to change everywhere.

```typescript
await driver.waitUntil(cond, { timeout: 4000, interval: 250 });   // why 4000?
await driver.pause(600);                                          // why 600?
```

**Sniff.** Numeric literals other than `0`/`1`/`-1` in a `+`-line expression (timeouts, durations, indices, ratios, limits), or the same literal appearing 3+ times across the change.

**Fix.** Name it where it belongs. This suite already centralizes timing: `TIMEOUTS` (`test/helpers/timeouts.ts`) for condition-wait tiers, `WAIT` (`test/data/constants.ts`) for deliberate settles — see [`appium/helpers-and-reuse.md`](../appium/helpers-and-reuse.md) for which module owns which. For anything else, a `const` next to its use with the reason in the name:

```typescript
const RN_ONCHANGE_SETTLE_MS = 600;   // RN fires onChange one frame after the wheel stops
await driver.pause(RN_ONCHANGE_SETTLE_MS);
```

**Do NOT flag** a number that *is* the test data (`setValue("6")`, `expectedRows === 3`) or an obvious geometric midpoint (`size.width / 2`).

---

## P2 — Mutable exported object where `as const` / `readonly` is meant

An exported config or lookup table declared as a plain object is (a) writable by any importer and (b) typed as widened `string`/`number`, so it can't drive a union or exhaustiveness check. `as const` fixes both at zero runtime cost.

```typescript
export const TIMEOUTS = { SHORT: 5000, MEDIUM: 15000, LONG: 30000 };
// TIMEOUTS.SHORT is `number`; any importer can write TIMEOUTS.SHORT = 1
```

**Sniff.** An exported `const` object/array literal of fixed values on `+` lines with no `as const` and no `readonly`/`Readonly<>` annotation.

**Fix.**

```typescript
export const TIMEOUTS = { SHORT: 5000, MEDIUM: 15000, LONG: 30000 } as const;
export type TimeoutTier = keyof typeof TIMEOUTS;   // "SHORT" | "MEDIUM" | "LONG" — now usable as a type
```

---

## P2 — Wrong container for the job: `class`, `object`, or loose functions

TypeScript gives three shapes for grouping code, and each says something different. Picking the wrong one is a readability and testability cost, not just style:

- **`class`** — when there is *state* or *identity* (a Page Object holding its selectors; a helper that must be instantiated per device). This suite exports page objects as **singletons** (`export default new LoginPage()`).
- **`class` with only `static` members, or an `object` literal of functions** — a namespace for related stateless helpers (`AppHelper`, `GestureHelper`, `ElementHelper`). Fine, and it's this suite's convention for helpers.
- **Plain exported functions** — a small set of independent utilities (`isAndroid`, `platformLocator`).

The finding is a mismatch: a `class` instantiated per call just to hold no state, a stateless helper that grew an instance field, or a fourth pattern invented alongside three existing ones.

**Sniff.** On `+` lines: a `class` with no instance fields whose methods are all called through a fresh `new`; instance state added to a helper the suite treats as stateless; a new grouping style (loose functions) in a directory where every sibling uses a `class`/`object` namespace.

**Fix.** Match the sibling files. Stateless → `static` methods or an exported `object`; stateful → a class, and if it's a page, export the singleton like every other page. Related: [`appium/page-objects.md`](../appium/page-objects.md) (cross-test state leak via the page singleton).

---

## Nit — Missing explicit return type on an exported function

An inferred return type is invisible at the call site and changes silently when the body changes — including from `Promise<boolean>` to `Promise<boolean | undefined>` after someone adds an early return.

**Sniff.** An `export`ed / `public` function or method on a `+` line with no `: <Type>` return annotation. Async action methods should read `Promise<void>` / `Promise<string>` explicitly.

**Fix.** Annotate it. This also documents the intent for the reader who never opens the body.

---

## Nit — Import and module hygiene

`require(...)` in a `.ts` file, unused imports left after an edit, a default and named import of the same module in one file, or a deep relative path (`../../../helpers/x`) where a barrel or path alias exists.

**Fix.** ES imports only, delete what you stopped using, and follow the import shape the neighbouring files use. (Overlaps [`appium/typescript-and-async.md`](../appium/typescript-and-async.md) → *Import / module hygiene* — post one finding, not two.)
