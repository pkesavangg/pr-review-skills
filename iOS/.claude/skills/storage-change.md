---
name: storage-change
description: Plan and implement a storage-related change safely. Use when a task touches SwiftData models, local repositories, Keychain, UserDefaults/KvStorage, or migration behavior.
---

Handle a storage, persistence, or migration change with explicit risk checks.

The storage-related change is: $ARGUMENTS

## Instructions

### 1 — Classify the Change

Determine which category applies:
- SwiftData model/schema change
- local repository behavior change
- keychain / token storage change
- kv-storage / defaults change
- migration or rollback change

Read the relevant source and docs first:
- affected files in `meApp/Domain/Models/DB/`
- affected files in `meApp/Data/Storage/` or `meApp/Data/Services/`
- `docs/KEYCHAIN_MIGRATION.md` if secure storage is involved

### 2 — Identify Risk Level

Assess whether the change can cause:
- data loss
- schema incompatibility
- rollback problems
- sensitive data leakage
- stale cached state across accounts/sessions

### 3 — Implement Using Existing Patterns

Apply the current repo approach:
- **keep secrets/tokens in Keychain, not SwiftData or KvStorage** — reference `/keychain-pattern` skill for comprehensive storage decision tree, sensitive data classification, and implementation patterns
- use in-memory containers in tests
- preserve account-scoped storage semantics
- document one-time migration behavior where needed

### 4 — Migration Risk Deep-Dive

If the change touches persistent schemas, secure storage behavior, or migration logic, perform a full risk review (previously handled by the `migration-risk-reviewer` agent):

**4a — Inspect the full storage path:**
- `meApp/Domain/Models/DB/` — all `@Model` classes that may be affected
- `meApp/Data/Storage/` — repository implementations
- `meApp/Data/Services/` — services that read/write stored data
- `docs/KEYCHAIN_MIGRATION.md` — prior migration decisions

**4b — Migration checklist — explicitly answer each:**
- What existing data is affected?
- Is a migration or backfill needed?
- Is rollback possible without data loss?
- Are there partial migration states that could leave data corrupted?
- **Are secrets/tokens stored in the wrong place?** — reference `/keychain-pattern` skill for storage location decision logic and migration patterns
- Does a doc need updating in `docs/`?
- Which tests should prove the migration path?
- Are there missing tests for one-time migration logic?

**4c — Assign risk level:**
- **Low** — additive change, no schema break, rollback trivial
- **Medium** — schema change with lightweight migration, some rollback risk
- **High** — destructive schema change, data loss possible, no migration plan

### 5 — Report

Return:
- change category
- risk level (Low / Medium / High)
- affected data
- missing safeguards or tests
- concrete remediation checklist
- required docs/tests/follow-up
