# {ISSUE_ID} — {ISSUE_TITLE}

> **Type:** {ISSUE_TYPE} | **Date:** {today's date} | **Status:** Draft

---

## Overview

{1–3 sentence plain-English description of what this issue is about and why it matters.}

---

## Problem Statement

{What is broken or missing? What user or system need does this address?}

---

## Acceptance Criteria

{Copy from Jira if available, otherwise derive from the description:}
- [ ] Criterion 1
- [ ] Criterion 2

---

## Proposed Solution

{High-level approach: which architectural layer is touched, what design pattern is applied.}

---

## Files to Create

| File | Purpose |
|------|---------|
| `meApp/Features/{Feature}/…` | … |
| `meAppTests/Features/{Feature}/…` | … |

---

## Files to Modify

| File | Change |
|------|--------|
| `meApp/…` | Add method `xyz()` |

---

## Files to Delete

| File | Reason |
|------|--------|
| — | — |

---

## Documentation Impact

Which maintained docs / Confluence pages this change will outdate. Derive from the source→doc
map (`scripts/docs-freshness-check.sh` / the `/update-architecture` Scope table) and the
Confluence map in `docs/overview/CONFLUENCE.md`. Put a single `—` row and tick "No documentation impact"
if the change touches nothing documented.

| Change area | Local doc to update | Confluence page |
|-------------|---------------------|-----------------|
| {e.g. new SwiftData model / schema field} | `docs/guides/DATABASE_SCHEMA.md` | meApp - Development (`1552482315`) |
| {e.g. new feature / service / DI} | `iOS/architecture.md` | meApp - Development (`1552482315`) |
| {e.g. new/changed skill · run.sh · CI · hook} | `docs/overview/CLAUDE_AUTOMATION.md` | meApp - Development (`1552482315`) |

- [ ] No documentation impact — the change touches nothing in the maps above.

---

## New Mocks Required

| Mock | Protocol | Location |
|------|----------|----------|
| `Mock{Name}` | `{Name}Protocol` | `meAppTests/Features/{Feature}/Mocks/` |

_(Run `/gen-mock-single` for each, or spawn `gen-mock-batch` agent if 2+)_

---

## Test Strategy

| File | Layer | Coverage Min | Key Scenarios |
|------|-------|-------------|---------------|
| `{ClassName}Tests.swift` | {layer} | {75/80/85}% | success, failure, edge case |

---

## Risks & Edge Cases

- {Risk 1}

---

## Open Questions

- {Any ambiguity needing clarification before starting?}

---

## Implementation Checklist

- [ ] Explore codebase, read affected files
- [ ] Create/modify production code files
- [ ] Generate required mocks
- [ ] Scaffold test file (`/gen-test-file`)
- [ ] Write all tests, reach coverage threshold
- [ ] Run `coverage-gap-finder` agent if any file is below threshold
- [ ] Update affected docs per **Documentation Impact** (`/update-architecture` → `architecture.md` + `docs/`)
- [ ] Mirror to Confluence if a page is affected (`/update-confluence` — drafts, then you approve before it writes)
- [ ] Self-review (`/self-review`)
- [ ] Commit, raise PR, log work
