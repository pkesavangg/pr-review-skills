---
name: refactor
description: Guide safe refactoring of existing Swift code. Use when the user says "refactor X", "rename Y to Z", "extract this into a service", "split this store", "clean up this file", or when restructuring code without changing external behaviour.
---

Refactor existing code safely without changing external behaviour.

The refactoring task is: $ARGUMENTS

## Instructions

### 1 — Define the Refactoring Scope

State clearly:
- what is being changed (type name, method, file structure)
- what will NOT change (public interface, behaviour, test contracts)
- the type of refactoring: rename / extract / split / inline / move / restructure

**Archetype reference:**

| Type | What to do | Key risks |
|------|-----------|-----------|
| **Rename** | Change type/method/property name across all call sites, mocks, and tests | Missed call sites, stale mock method names |
| **Extract service** | Move methods to a new protocol + concrete class, register in DI, inject into callers | DI registration gap, test mock missing |
| **Extract protocol** | Pull interface from concrete class, update callers to use protocol type | Callers referencing concrete-only members |
| **Split store** | Separate a store into two stores with distinct responsibilities | Navigation wiring, shared state coordination |
| **Inline** | Collapse a trivial wrapper/helper back into its callers | Multiple callers needing the same change |
| **Move** | Relocate a file to a different feature/layer directory | Import paths, Xcode group references |
| **Restructure** | Reorganize folder layout or layer boundaries without changing interfaces | Build order, circular references |

---

### 2 — Assess Blast Radius Before Touching Anything

Search for all usages of the type or method being changed:

```bash
rg -n "{TypeOrMethodName}" meApp meAppTests -g '*.swift' | head -30
```

If the symbol is used in more than 5 files, spawn the `di-impact-finder` agent to get a full impact map before proceeding.

Flag any usage in:
- `DependencyContainer.swift` / `ServiceRegistry.swift` registrations
- `@Injector` call sites
- existing test files (mock setups, `makeSUT` factories)
- `Domain/` interfaces (protocol method signatures)

---

### 3 — Run Regression Baseline

Before making any changes, read and execute `.claude/skills/review-regression.md` on the files in scope. Note the current risk level and any existing issues. The refactoring must not increase it.

---

### 4 — Implement the Refactoring

Make the structural change. Do not fix bugs, add features, or improve logic while refactoring.
- One rename or extraction at a time
- Update all call sites consistently
- Update mocks to match any renamed methods or constructor signatures
- Update test `makeSUT` factories if constructor signatures changed

---

### 5 — Verify Compilation

Build immediately after the change:

```bash
xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -configuration Dev \
  -destination 'generic/platform=iOS' \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

Fix all build errors before running tests. A refactoring that does not compile is not done.

---

### 6 — Verify Tests Still Pass

Find a connected physical device and run tests on it:

```bash
# Find device ID
xcodebuild -project meApp.xcodeproj -scheme meAppTests -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator | head -1

# Run tests
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

No test should fail due to the refactoring. If tests fail, fix them — do not delete them.

---

### 7 — Report

```
Refactoring type: <rename / extract / split / move / restructure>
Symbol changed: <OldName> → <NewName> (or description)
Files modified: <count and list>
Call sites updated: <count>
Mocks updated: <count>
Build: PASS
Tests: PASS / FAIL (with details)
```

Follow up with `/self-review` before committing.
