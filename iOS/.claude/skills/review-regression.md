---
name: review-regression
description: Check whether a PR risks breaking existing behaviour outside its stated scope — protocol changes, stale tests, DI registration gaps, SwiftData migrations, and build config parity. Use when reviewing a PR for regressions, or when the user says "check for regressions", "regression review", "will this break anything". Also called automatically by /self-review and /review-pr.
---

Check whether the PR risks breaking existing behaviour outside its stated scope.

Inputs available: PR_META (number, title, branch), DIFF (full patch text), CHANGED_FILES (list), WORKTREE_PATH

## Instructions

Work through each area below. Use the worktree to read source files for context where the diff alone is insufficient.

---

### 1 — Public API Surface (Protocols & Repositories)

For each changed file in CHANGED_FILES:
- If the file is under `Domain/Repositories/` or `Domain/Services/`, or is named `*Protocol.swift`:
  - Read the file in the worktree and compare to the diff
  - Flag any method signatures that were **removed** or **changed** (parameter types, labels, return type, `async`/`throws` modifiers)
  - Check all conforming types are updated consistently (search for the protocol name across the worktree)

Risk: **High** if a protocol method was removed/changed without updating all conformances.
Risk: **Medium** if a new required method was added to a protocol (existing conformances need updating).

---

### 2 — Existing Tests Still Valid

For each source file in CHANGED_FILES, find its corresponding test file:
- Pattern: `meAppTests/Features/{Feature}/` for source files under `Features/{Feature}/`
- Pattern: `meAppTests/Features/{Feature}/{Type}Tests.swift` for `Data/API/` or `Data/Services/` files

For each test file found:
1. Read the test file from the worktree
2. Scan for test methods that call the changed function/method
3. Verify the test assertions still logically match the new implementation:
   - If a function's return type changed, do the `#expect` calls still make sense?
   - If a function's behaviour changed (e.g. error conditions), are the failure tests still valid?
   - If parameters were added/removed, do mock setups still reflect reality?

Flag tests that appear **logically stale** (they pass but no longer test the right thing) as **WARNING**.
Flag tests that would **fail to compile** due to the change as **FAIL**.

---

### 3 — Dependency Impact (Scope Creep Check)

For each changed file, search the worktree for usages:

```bash
grep -r "{TypeOrFunctionName}" {WORKTREE_PATH}/iOS/meApp --include="*.swift" -l
```

- List all files that import or reference the changed type/function
- Check that none of those callers are broken by the change
- Flag if the change has a wider blast radius than the PR description suggests

Risk: **High** if a shared utility (e.g. `HTTPClient`, `DependencyContainer`, `AppContext`) was changed and many features depend on it.
Risk: **Medium** if a shared model or service was changed.
Risk: **Low** if the change is isolated to one feature module.

---

### 4 — SwiftData Model Changes

If any file under `Domain/Models/DB/` appears in CHANGED_FILES:
- Check if any `@Model` class had properties **added**, **removed**, **renamed**, or had their **type changed**
- SwiftData requires a migration scheme for schema changes in production
- Check if a `VersionedSchema` or `SchemaMigrationPlan` was added/updated alongside the model change

Flag as **FAIL** if a `@Model` schema changed with no migration plan.
Flag as **WARNING** if properties were only added (lightweight migration may suffice but should be confirmed).

---

### 5 — DI Registration

If a new protocol conformance or service was introduced in the diff:
- Search the worktree for `DependencyContainer.swift` and confirm the new type is registered
- Search for `@Injector` usages of the new protocol to confirm resolve path exists

```bash
grep -r "register(" {WORKTREE_PATH}/iOS/meApp/Core/DI/ --include="*.swift"
```

Flag as **FAIL** if a new injectable dependency has no registration.

---

### 6 — Build Config & Endpoint Parity

If any of these files appear in CHANGED_FILES:
- `EndPoints.swift` — confirm new endpoint cases are handled in both `Dev` and `Production` `urlRequest` implementations
- `Info.plist` — flag any key changes that could differ between configurations
- `AppConstants.swift` or `Environment.swift` — confirm Dev/Production parity

Flag as **WARNING** if endpoint/config changes look asymmetric across environments.

---

## Output

```
### Regression Review

| Area | Risk | Notes |
|------|------|-------|
| Public API Surface | Low / Medium / High / N/A | … |
| Existing Tests | Low / Medium / High / N/A | … |
| Dependency Impact | Low / Medium / High / N/A | … |
| SwiftData Models | Low / Medium / High / N/A | … |
| DI Registration | Low / Medium / High / N/A | … |
| Build Config Parity | Low / Medium / High / N/A | … |

**Regression risk:** Low / Medium / High

Findings:
- [file:line] Description of regression risk and recommended action
```

Overall risk rules:
- Any **High** area → Regression risk = High
- Any **Medium**, no High → Regression risk = Medium
- All **Low** or **N/A** → Regression risk = Low
