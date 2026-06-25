---
name: orchestra
description: Master workflow orchestration guide. Defines how skills, commands, and agents compose into end-to-end workflows. Reference this for task sequencing, review pipelines, subagent strategy, and verification checklists. Auto-loaded via iOS/CLAUDE.md.
---

# Workflow Orchestration

> This file defines how skills, commands, and agents compose into end-to-end workflows. Follow these orchestration rules for every non-trivial task.

---

## 1. Core Principles

| Principle | Rule |
|-----------|------|
| **Plan first** | Enter plan mode for any task with 3+ steps or architectural decisions. If something goes sideways, STOP and re-plan immediately. |
| **Simplicity first** | Make every change as simple as possible. Minimal code impact. No side effects. |
| **No laziness** | Find root causes. No temporary fixes. Senior developer standards. |
| **Minimal impact** | Only touch what's necessary. No unrelated cleanup or improvements. |
| **Demand elegance** | For non-trivial changes, pause and ask "is there a more elegant way?" Skip for simple, obvious fixes. |
| **Verify before done** | Never mark a task complete without proving it works. Run tests, check logs, demonstrate correctness. |

---

## 2. Task Management Protocol

Every non-trivial task follows this lifecycle:

```
1. Write plan to tasks/todo.md with checkable items
2. Check in with user before starting implementation
3. Mark items complete as you go
4. High-level summary at each step
5. Add review section to tasks/todo.md when done
6. Update tasks/lessons.md after any correction
```

**Self-improvement loop:** After ANY correction from the user, immediately update `tasks/lessons.md` with:
- The mistake pattern
- A rule that prevents recurrence
- When the rule applies

---

## 3. Subagent Strategy

Use subagents liberally to keep the main context window clean:

| When | Agent |
|------|-------|
| Planning an API-backed feature | `api-change-planner` |
| Checking test coverage gaps | `coverage-gap-finder` |
| Assessing DI registration impact | `di-impact-finder` |
| Generating 2+ mocks at once | `gen-mock-batch` |
| Broad codebase exploration | Explore subagent |
| Parallel independent research | Multiple subagents simultaneously |

**Rules:**
- One focused task per subagent
- Never duplicate work a subagent is already doing
- Offload research, exploration, and parallel analysis
- Use background agents for independent work

---

## 4. Workflow Sequences

### 4.1 Full Ticket Workflow (SDLC)

The `/work-ticket` command orchestrates the complete flow. When working a Jira ticket end-to-end, follow this sequence:

```
/fetch-ticket          → Gather requirements and acceptance criteria
/create-branch         → Branch from develop, transition Jira to In Progress
/create-prd            → Write implementation plan (for non-trivial tasks)
  ↓
[Implementation phase — use skills as needed per task type]
  ↓
/post-change-guard     → Auto-fix lint/a11y/concurrency; report security & standards; build if vital files changed
  ↓
/verify-tests          → Build + run tests + coverage check
/self-review           → Run all 5 specialist reviews
/commit                → Stage and commit with Jira ID prefix
/raise-pr              → Push and create PR via gh CLI
/log-work              → Log time on Jira issue
```

### 4.2 New Feature Implementation

```
/fetch-ticket                    → Requirements
/create-prd                      → Plan in iOS/docs/plans/
  ↓
agent: api-change-planner        → Map affected layers and files
  ↓
/feature-slice                   → Scaffold feature module (includes #Preview + accessibility)
/add-endpoint                    → Add API endpoint (if needed)
/wire-service                    → Register service in DI
/wire-navigation                 → Wire screen into routing
/add-strings                     → Add string constants
  ↓
[Build feature code]
  ↓
/add-accessibility               → Add accessibility labels/identifiers to all new views
/add-preview                     → Add #Preview blocks to new views and components
  ↓
/gen-test-file                   → Scaffold unit tests
/gen-ui-test-file                → Scaffold UI tests (flags zero-coverage features)
/gen-mock-single or              → Generate required mocks
  agent: gen-mock-batch
  ↓
/post-change-guard               → Auto-fix quality issues; build check if vital files touched
  ↓
/verify-tests                    → Build + test + coverage
/self-review                     → Full review pipeline
/commit → /raise-pr → /log-work
```

### 4.3 Bug Fix

```
/fetch-ticket                    → Understand the bug
/debug-issue                     → Investigate root cause systematically
  ↓
/fix-bug                         → Fix with regression test
  ↓
/post-change-guard               → Auto-fix quality issues; build check if services/DI touched
  ↓
/verify-tests                    → Confirm fix + no regressions
/self-review                     → Full review pipeline
/commit → /raise-pr → /log-work
```

### 4.4 Refactoring

```
/create-prd                      → Document scope and constraints
agent: di-impact-finder          → Assess DI impact (if touching services)
  ↓
/refactor                        → Execute refactor without behavior changes
/update-mock                     → Update any affected mocks
  ↓
/post-change-guard               → Build always triggered (refactors touch DI/services)
  ↓
/verify-tests                    → All existing tests must still pass
/self-review                     → Full review pipeline
/commit → /raise-pr
```

### 4.5 Adding a New API Endpoint

```
agent: api-change-planner        → Plan layers to touch
  ↓
/add-endpoint                    → DTO + Repository + Service wiring
/wire-service                    → DI registration (if new service)
  ↓
/gen-test-file                   → Tests for new repository/service
/gen-mock-single                 → Mock for new protocol
  ↓
/post-change-guard               → Build always triggered (touches Domain/Repositories, Data/API)
  ↓
/verify-tests → /self-review → /commit
```

### 4.6 Test Coverage Improvement

```
agent: coverage-gap-finder       → Identify uncovered methods and branches
  ↓
/gen-test-file                   → Scaffold test file (or add to existing)
/gen-mock-single or              → Generate missing mocks
  agent: gen-mock-batch
  ↓
/verify-tests                    → Confirm coverage meets thresholds
/commit
```

### 4.7 Storage / Data Model Change

```
/storage-change                  → Risk assessment and migration check
/swiftdata                       → Apply SwiftData rules
  ↓
[Implementation]
  ↓
/verify-tests → /self-review → /commit
```

### 4.8 Dashboard Graph Change

```
/graph                           → Follow graph layer rules
/swift-concurrency               → Apply concurrency patterns (if async)
  ↓
/verify-tests → /self-review → /commit
```

### 4.9 Configuration / Environment Change

```
/config-change                   → Safe config modification
  ↓
/build                           → Quick build verification
/verify-tests → /self-review → /commit
```

### 4.10 PR Review

```
/review-pr {url}                 → Full automated review pipeline
```

This command internally runs: security, lint, regression, issue-coverage, and accessibility reviews, then posts a consolidated comment.

### 4.11 Fix PR Comments

```
/fix-pr-comments {url}           → Triage + auto-fix reviewer comments
```

Fetches all inline and general review comments, classifies each as Auto-fix / Needs discussion / Skip, applies safe concrete fixes, and reports a summary table. Does NOT commit. Run after `/review-pr` feedback lands.

Trigger phrases: "fix PR comments", "address review feedback", "apply reviewer suggestions", "respond to PR feedback", "fix the review comments", "act on code review", "resolve PR comments".

### 4.12 Release Preparation

```
/release-cut                     → Gather changes, group by theme, write release notes
```

---

## 5. Review Pipeline

Before any commit on non-trivial changes, `/self-review` runs these 5 specialist reviews in order:

| Step | Skill | Focus |
|------|-------|-------|
| 1 | `/review-lint` | SwiftLint rules, style conventions |
| 2 | `/review-regression` | Breaking changes in API, tests, DI, SwiftData |
| 3 | `/review-security` | Secrets, force unwrap, Keychain, HTTP, logging, actor isolation |
| 4 | `/review-issue-fix` | Does the PR address Jira acceptance criteria? |
| 5 | `/review-accessibility` | VoiceOver, labels, identifiers, dynamic type |

**Rule:** Fix all findings before committing. Do not skip reviews.

---

## 6. Verification Checklist

Before marking any task complete, confirm:

- [ ] Code builds without warnings (`/build`)
- [ ] All tests pass on physical device (`/run-tests`)
- [ ] Coverage meets layer thresholds (`/verify-tests`)
- [ ] Self-review passes all 5 checks (`/self-review`)
- [ ] No unrelated files modified
- [ ] Commit message follows `MOB-XXXX Description` format
- [ ] Architecture doc updated if structural changes made (`/update-architecture`)

---

## 7. Skill Reference by Category

### Planning & Research
| Skill/Agent | Purpose |
|-------------|---------|
| `/fetch-ticket` | Fetch Jira issue details |
| `/create-prd` | Generate implementation plan |
| `/read-figma` | Extract design context from Figma |
| `/read-jira-images` | Analyze Jira image attachments |
| `api-change-planner` | Map API feature impact across layers |
| `di-impact-finder` | Assess DI registration impact |
| `coverage-gap-finder` | Find uncovered methods and branches |

### Scaffolding & Wiring
| Skill | Purpose |
|-------|---------|
| `/feature-slice` | Scaffold feature module structure (includes #Preview + accessibility) |
| `/add-endpoint` | Add API endpoint end-to-end |
| `/wire-service` | Register service in DI system |
| `/wire-navigation` | Wire screen into routing |
| `/add-strings` | Add string constants |
| `/add-accessibility` | Add accessibility labels, identifiers, and Dynamic Type to a view |
| `/add-preview` | Scaffold #Preview blocks with mock data for a view |

### Implementation Guides
| Skill | Purpose |
|-------|---------|
| `/fix-bug` | Fix bug with regression test |
| `/refactor` | Safe refactoring without behavior changes |
| `/debug-issue` | Systematic bug investigation |
| `/config-change` | Safe config modification |
| `/storage-change` | Storage/persistence changes |
| `/graph` | Dashboard graph layer changes |
| `/swift-concurrency` | Concurrency patterns |
| `/swiftdata` | SwiftData rules and patterns |
| `/analytics` | Structured logging + Crashlytics non-fatal for critical paths |
| `/theme-guide` | Theme system — colors, typography, spacing, border radius |
| `/api-guide` | API call patterns — HTTPClient, Endpoint, DTO, RepositoryAPI |
| `/form-guide` | Form validation — ObservableForm, FormControl, validators |
| `/logging-guide` | Logging system — LoggerService, persistence, retention, server submission |
| `/notification-guide` | Notification layer — alerts, toasts, loaders, modals, two-window architecture |

### Testing & Mocks
| Skill/Agent | Purpose |
|-------------|---------|
| `/gen-test-file` | Scaffold unit test file |
| `/gen-ui-test-file` | Scaffold UI test file |
| `/gen-mock-single` | Generate mock for one protocol |
| `/update-mock` | Update mock for changed protocol |
| `gen-mock-batch` | Generate mocks for 2+ protocols |
| `/run-tests` | Quick build + test on device |
| `/verify-tests` | Build + test + coverage enforcement |

### Review & Quality
| Skill | Purpose |
|-------|---------|
| `/post-change-guard` | Mid-session fix + check (lint/a11y/concurrency auto-fix, security + standards report, build if vital files) |
| `/self-review` | Run all 5 specialist reviews |
| `/review-lint` | SwiftLint and style check |
| `/swiftlint` | Run SwiftLint with auto-fix, then manually fix remaining violations |
| `/review-regression` | Breaking change detection |
| `/review-security` | Security audit |
| `/review-issue-fix` | Jira acceptance criteria check |
| `/review-accessibility` | Accessibility audit (with optional `--fix` auto-repair mode) |

### Git & Delivery
| Skill/Command | Purpose |
|---------------|---------|
| `/create-branch` | Branch from develop with Jira ID |
| `/commit` | Stage and commit with Jira prefix |
| `/raise-pr` | Push and create PR |
| `/log-work` | Log time on Jira |
| `/review-pr` | Full PR review pipeline |
| `/fix-pr-comments` | Triage and auto-fix reviewer comments on a PR |
| `/release-cut` | Release notes generation |

### Documentation
| Skill | Purpose |
|-------|---------|
| `/update-architecture` | Update architecture.md |
| `/build` | Quick build verification |

---

## 8. Autonomous Bug Fixing Protocol

When given a bug report, execute without asking for hand-holding:

1. `/fetch-ticket` — gather context
2. `/debug-issue` — investigate systematically (logs, errors, failing tests)
3. `/fix-bug` — implement fix with regression test
4. `/verify-tests` — confirm fix, no regressions
5. `/self-review` — full review pipeline
6. `/commit` → `/raise-pr` — deliver

Only pause to ask the user if the root cause is ambiguous or the fix has significant blast radius.

---

## 9. Parallelization Opportunities

These skill groups can run concurrently when their inputs are independent:

| Parallel Group | Skills |
|----------------|--------|
| post-change-guard internals | `/swiftlint` + `/review-accessibility --fix` + `/review-security` + `/review-code-standards` (Steps 3a–3d run concurrently) |
| Mock generation | `gen-mock-batch` (handles multiple protocols in parallel) |
| Review pipeline | `/review-lint` + `/review-security` + `/review-accessibility` (read-only checks) |
| Research phase | `/fetch-ticket` + `/read-figma` + `/read-jira-images` |
| Impact analysis | `api-change-planner` + `di-impact-finder` |

---

## 10. Error Recovery

| Situation | Action |
|-----------|--------|
| Build fails | Fix compilation errors, re-run `/build` |
| Tests fail | Investigate failure, fix, re-run `/run-tests` |
| Coverage below threshold | Run `coverage-gap-finder`, add tests, re-run `/verify-tests` |
| Review finds issues | Fix all findings, re-run the specific review skill |
| Plan goes sideways | STOP. Re-enter plan mode. Re-assess approach. |
| User corrects you | Update `tasks/lessons.md` immediately. Apply the lesson. |
