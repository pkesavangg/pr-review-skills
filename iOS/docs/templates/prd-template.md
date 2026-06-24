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
- [ ] Self-review (`/self-review`)
- [ ] Commit, raise PR, log work
