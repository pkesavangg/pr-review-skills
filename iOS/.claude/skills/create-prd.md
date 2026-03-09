Generate a Product Requirements Document (PRD) / implementation plan as a markdown file before starting work on a Jira issue or task.

The Jira issue ID or task description is: $ARGUMENTS

---

## Instructions

### 1 — Fetch Ticket Details

If `$ARGUMENTS` is a Jira issue ID (e.g. `MA-3319`):
- Call `getAccessibleAtlassianResources` to retrieve the `cloudId`
- Call `getJiraIssue` with `cloudId` and the issue ID
- Extract: `ISSUE_ID`, `ISSUE_TITLE`, `ISSUE_TYPE` (Bug / Story / Task), `ISSUE_DESCRIPTION`, `ACCEPTANCE_CRITERIA`, `LINKED_ISSUES`

If `$ARGUMENTS` is a plain description (no issue ID):
- Set `ISSUE_ID = "LOCAL"`, `ISSUE_TITLE = $ARGUMENTS`, `ISSUE_TYPE = "Task"`, `ISSUE_DESCRIPTION = ""`
- Skip Jira lookup

---

### 2 — Explore the Codebase

Based on the issue title and description, search the relevant areas of the codebase in parallel:

- Find existing source files related to the feature/bug area:
  ```bash
  grep -r "{keyword}" /Users/kesavan/meApp-1/iOS/meApp --include="*.swift" -l
  ```
- List the feature's current directory structure (if applicable):
  ```bash
  ls -R /Users/kesavan/meApp-1/iOS/meApp/Features/{Feature}/
  ```
- Check for existing tests:
  ```bash
  ls /Users/kesavan/meApp-1/iOS/meAppTests/Features/{Feature}/
  ```
- Check for existing mocks:
  ```bash
  ls /Users/kesavan/meApp-1/iOS/meAppTests/Features/{Feature}/Mocks/ 2>/dev/null
  ```

Read the most relevant 2–3 source files to understand current implementation patterns.

---

### 3 — Determine Implementation Approach

Based on the ticket type and codebase exploration, assess:

**For a new feature:**
- What feature folder it lives in (existing or new)
- Which layers are affected: Domain model, Repository protocol, API repository, Service, Store, Views
- Whether new DI registrations are needed
- Whether new endpoints are needed in `enum Endpoint`

**For a bug fix:**
- Root cause area (API layer, service logic, store state, SwiftData, UI binding)
- Files that contain the faulty code

**For test additions:**
- Which files need tests
- Coverage threshold for each (API: 75%, Services: 80-85%, Stores/Forms: 80-85%)
- Which mocks already exist vs need to be generated

---

### 4 — Write the PRD File

Slugify the issue title:
- Lowercase, replace spaces/special chars with hyphens, trim trailing hyphens
- Example: `MA-3319-add-entry-sync-to-appsync-service`

Write the file to:
```
/Users/kesavan/meApp-1/iOS/docs/plans/{ISSUE_ID}-{slugified-title}.md
```

Use this template:

```markdown
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

{High-level approach: which architectural layer is touched, what design pattern is applied (new service method, new store action, new repository call, etc.)}

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

_(Run `/gen-mock` for each, or spawn `mock-generator` agent if 2+)_

---

## Test Strategy

| File | Layer | Coverage Min | Key Scenarios |
|------|-------|-------------|---------------|
| `{ClassName}Tests.swift` | {Services/API/Stores} | {75/80/85}% | success, failure, edge case |

---

## Risks & Edge Cases

- {Risk 1: e.g., SwiftData migration needed if model changes}
- {Risk 2: e.g., Auth token expiry during sync}
- {Risk 3: e.g., HealthKit permission not granted}

---

## Open Questions

- {Any ambiguity in the ticket that needs clarification before starting?}

---

## Implementation Checklist

- [ ] Explore codebase, read affected files
- [ ] Create/modify production code files
- [ ] Generate required mocks (`/gen-mock` or `mock-generator` agent)
- [ ] Scaffold test file (`/gen-test-file`)
- [ ] Write all tests, reach coverage threshold
- [ ] Run `/coverage-gap-finder` if any file is below threshold
- [ ] Self-review (`/self-review`)
- [ ] Commit, raise PR, log work
```

---

### 5 — Confirm Output

Print:
```
PRD created: iOS/docs/plans/{ISSUE_ID}-{slugified-title}.md

Key decisions captured:
- Affected layers: {list}
- Files to create: {count}
- Files to modify: {count}
- New mocks needed: {count}
- Test files needed: {count}

Review the PRD before starting implementation.
```
