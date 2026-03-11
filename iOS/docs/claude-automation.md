# Claude Code Automation — meApp iOS

This document describes the current Claude automation layer for `meApp`: skills, commands, agents, and hooks. The goal is end-to-end development support across ticket workflow, API work, DI wiring, persistence changes, logging/instrumentation, release workflow, and review.

---

## Overview

The automation layer lives under `.claude/`:

```text
.claude/
├── settings.json
├── agents/
├── commands/
└── skills/
```

As of 2026-03-11 the repo contains:
- 36 skills
- 3 commands
- 4 agents
- 2 hooks (1 pre-commit, 1 post-edit)

The automation is intentionally split into:
- **development skills** — plan and wire changes safely
- **test skills** — scaffold and verify coverage
- **review skills** — catch regressions before commit/PR
- **workflow commands** — chain the above into full ticket or release flows

---

## Hooks

Configured in [settings.json](../.claude/settings.json).

### Pre-Commit Self-Review Reminder

Runs before any `git commit` Bash command:
- detects the commit command in the tool input
- prints a reminder that `/self-review` (lint, regression, security, issue coverage, accessibility) should be run first

This is a soft prompt — it does not block the commit.

### SwiftLint On Swift Edits

Runs after every `Edit` or `Write` on a `.swift` file:
- resolves the repo root dynamically via `git rev-parse`
- lints only the changed file using `.swiftlint.yml`
- skips `DerivedData` and `SourcePackages`
- prints up to 50 violations; truncates with a count if more

---

## Skills

### Ticket And Workflow Setup

`/fetch-ticket`
- Fetch full Jira issue details via MCP and display a structured summary.

`/create-branch`
- Derive a branch name from the Jira ID and summary, create it, and transition the ticket to In Progress.

`/create-prd`
- Explore the codebase and write an implementation plan to `docs/plans/` before coding begins.

`/commit`
- Stage targeted files and create a Jira-prefixed commit. Does not add `Co-Authored-By` trailers.

`/raise-pr`
- Push the branch, create a structured GitHub PR, and transition Jira to In Review.

`/log-work`
- Add a Jira worklog entry with an implementation summary and PR link.

---

### Development Skills

`/add-endpoint`
- Add a backend call end-to-end across `enum Endpoint`, request/response DTOs, API repository protocol, concrete API repository, service, and store layers.

`/add-strings`
- Add PascalCase string constants to the correct feature `Strings/` struct. Replaces hardcoded literals at call sites.

`/analytics`
- Add structured `LoggerService` instrumentation to a store, service, or repository. Encodes the project's level conventions (`.info` start, `.success` completion, `.error` failure, `.debug` console-only), data field rules, and PII-avoidance constraints.

`/build`
- Quick build-only verification. Does not run tests.

`/config-change`
- Safely update `xcconfig`, `Environment.swift`, or `Info.plist`-backed settings with `Dev`/`Production` parity checks.

`/debug-issue`
- Trace the execution path for a bug, identify the root cause, and recommend the right fix skill before editing any code.

`/feature-slice`
- Scaffold a feature module using the nearest existing archetype in the repo (simple standalone, nested settings, scale-setup subflow, form-driven, API-backed store).

`/fix-bug`
- Apply a targeted fix and add a regression test that would have caught the bug before it shipped.

`/graph`
- Fix or enhance the Dashboard graph layer. Classifies the problem (data, crosshair, Y-axis, X-axis, animation, scroll, goal chip, new section) and applies the correct layer's fix.

`/refactor`
- Guide safe renames, extractions, splits, moves, and inline operations. Checks blast radius before changing anything and verifies tests still pass after.

`/storage-change`
- Handle SwiftData schema changes, Keychain modifications, KvStorage changes, or migration behavior — includes full migration risk deep-dive (data loss, rollback, partial-state, test coverage).

`/swift-concurrency`
- Apply the repo's actor isolation, task cancellation, value-transfer, and Sendable rules. Includes chart-layer-specific patterns (throttled `DispatchWorkItem`, deferred `@State` mutation, cancellable period-change tasks).

`/swiftdata`
- Apply the repo's SwiftData rules: background contexts, `PersistentIdentifier` + refetch patterns, relationship reconstruction, in-memory test containers, and migration-safe edits.

`/update-architecture`
- Update `architecture.md` after a structural change (new feature, service, model, SPM dependency, or rename). Scans `.claude/skills/` and `.claude/agents/` for stale type references.

`/wire-navigation`
- Wire a new screen into the feature's `Route` enum and `RoutingView`. Adds the navigation trigger method to the calling store.

`/wire-service`
- Register and inject a new service or dependency through `ServiceRegistry`, `DependencyContainer`, and `TestDependencyContainer`.

---

### Test And Verification Skills

`/gen-mock-single`
- Generate a mock for a single protocol using the repo's conventions: Pattern A (Service — `Result` stubs + call tracking), Pattern B (Repository — in-memory backing + `Error?` stubs), Pattern C (API Repository — use `MockHTTPClient` instead).

`/gen-test-file`
- Scaffold a complete Swift Testing unit test file (`@Suite(.serialized)`, `@MainActor`, `makeSUT()` factory, success + failure paths per method, coverage target comment).

`/gen-ui-test-file`
- Scaffold a UI test file (`XCTestCase`) for a screen or flow. Lists missing `accessibilityIdentifier` values that must be added to the source screen before tests can target them.

`/run-tests`
- Build and run unit or UI tests on a connected physical device. Reports pass/fail without coverage analysis.

`/update-mock`
- Sync an existing mock to match its protocol's current signature. Handles added, removed, and changed methods; updates test call sites.

`/verify-tests`
- Build and run tests on a physical device, generate a coverage report, and enforce layer thresholds. Adds targeted tests for any file below its minimum before proceeding.

> **Note:** All test skills require a connected physical iOS device. The iOS Simulator is not supported — `GGBluetoothSwiftPackage`, `gWifiScalePackage`, and `AppSyncPackage` do not run on the simulator.

---

### Review Skills

`/review-accessibility`
- Check new and modified SwiftUI views for accessibility labels, `accessibilityHidden` on decorative elements, and `accessibilityIdentifier` for UI-tested elements.

`/review-issue-fix`
- Check whether the implementation actually satisfies the linked Jira acceptance criteria. Maps each criterion to code changes and test coverage.

`/review-lint`
- Check changed Swift files against the active `.swiftlint.yml` config and project manual style rules.

`/review-regression`
- Review protocol surface changes, stale tests, DI registration gaps, SwiftData migration risk, and `Dev`/`Production` endpoint parity.

`/review-security`
- Review hardcoded secrets, force unwrap in critical paths, Keychain vs. UserDefaults data routing, insecure HTTP URLs, PII in logs, and actor isolation risks.

`/self-review`
- Aggregate all five specialist reviews (lint, regression, security, issue coverage, accessibility) before committing. Blocks commit on any FAIL or High regression risk.

---

### Design Asset Reading Skills

`/read-figma`
- Extract screen names, component names, text content, design tokens, and interaction notes from a Figma URL. Called automatically by `/work-ticket` when a Figma link is found in the ticket description.

`/read-jira-images`
- Download and visually analyze image attachments (PNG, JPG, GIF, WebP) from a Jira issue using Claude's vision. Called automatically by `/work-ticket` when image attachments are present.

---

## Commands

### `/work-ticket`

Full ticket lifecycle from Jira to merged PR:

1. Fetch ticket and display structured summary
2. Read design assets — Figma URL (if present) and image attachments (if present), in parallel
3. Set original estimate on the Jira ticket (if not already set)
4. Create git branch and transition Jira to In Progress
5. Explore codebase, write PRD to `docs/plans/`, present interactive plan — pause for approval
6. Implement using the appropriate development skills (scaffolding agents run in background as needed)
7. Build and test verification — unit or UI tests with coverage thresholds enforced
8. Self-review (lint + regression + security + issue coverage + accessibility) — mandatory, non-skippable
9. Commit
10. Raise PR and transition Jira to In Review
11. Log worklog with PR link

### `/review-pr`

Full PR review workflow for one or more PR numbers or URLs:
- fetch PR metadata and extract Jira ID
- create isolated git worktrees per PR
- run security, lint, regression, issue-coverage, and accessibility review per PR
- write a consolidated dated report to `meAppTests/Reports/`
- optionally post individual review blocks as GitHub PR comments via `gh`

### `/release-cut`

Release preparation workflow:
- extract `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` from `project.pbxproj`
- gather iOS-only git history for the requested window
- group changes into product-facing themes
- write a release notes draft to `docs/plans/`
- summarize open release risks

---

## Agents

Agents run autonomously and can be spawned in the background during implementation.

`api-change-planner`
- Map a proposed API-backed feature or bug fix to the exact files and layers that should change. Returns a concrete plan with reference files, risks, and recommended follow-up skills.

`coverage-gap-finder`
- Run the full test + coverage pipeline on a physical device, read the generated report, and output a prioritized checklist of uncovered methods, missing failure paths, and untested guard branches per file.

`di-impact-finder`
- Find all DI registrations, `@Injector` call sites, and test container registrations that must be updated for a changed service or protocol. Returns a prioritized checklist.

`gen-mock-batch`
- Generate mock implementations for 2 or more Swift protocols simultaneously in parallel. Used by `/work-ticket` when a feature requires multiple new mocks at once.

---

## Recommended Usage

### Work A Full Ticket End-To-End

```text
/work-ticket MA-XXXX
```

All steps are orchestrated automatically with pause points for plan approval and pre-commit confirmation.

### Build A Backend Feature Manually

```text
/create-prd MA-XXXX
/add-endpoint <description>
/wire-service <description>
/gen-mock-single <ProtocolName>
/gen-test-file <SourceFile>
/verify-tests
/self-review
/commit
```

### Add A Nested Feature Or Settings Flow

```text
/feature-slice <description>
/wire-navigation <screen name>
/wire-service <description>
/analytics <store name>
/verify-tests
/self-review
```

### Instrument An Existing Store

```text
/analytics <StoreName>
```

### Investigate A Bug Before Coding

```text
/debug-issue <description>
→ /fix-bug (after root cause identified)
→ /self-review
→ /commit
```

### Keep Architecture Docs Current

```text
/update-architecture <what changed>
```

### Prepare A Release Note Draft

```text
/release-cut this week
```

---

## Alignment With This Repo

The automation is driven by recurring patterns visible in the codebase:

- Centralized DI and lifecycle wiring in [ServiceRegistry.swift](../meApp/Core/Services/ServiceRegistry.swift)
- Type-based dependency resolution in [DependencyContainer.swift](../meApp/Core/DI/DependencyContainer.swift)
- Endpoint-centric networking in [EndPoints.swift](../meApp/Domain/Models/API/EndPoints.swift)
- Actor-bound async work and value-transfer patterns in [SwiftDataWorker.swift](../meApp/Core/Services/SwiftDataWorker.swift)
- Migration-sensitive storage changes documented in [KEYCHAIN_MIGRATION.md](KEYCHAIN_MIGRATION.md)
- SwiftData repository/context patterns in [EntryRepository.swift](../meApp/Data/Storage/DB/EntryRepository.swift)
- Graph-specific architecture in [DashboardGraphManager.swift](../meApp/Features/Dashboard/Managers/DashboardGraphManager.swift), [YAxisCalculator.swift](../meApp/Features/Dashboard/Models/YAxisCalculator.swift), and [BaseGraphView.swift](../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift)
- Environment parity rules in [XCCONFIG_STRUCTURE.md](XCCONFIG_STRUCTURE.md)
- Structured `LoggerService` instrumentation in [LoggerService.swift](../meApp/Data/Services/LoggerService.swift)

The test-focused automation remains because the repo depends on:
- heavy protocol-based mock usage with consistent conventions
- explicit per-layer coverage thresholds
- separate unit/UI verification paths (physical device required)
- pre-commit review discipline enforced by hooks and `/self-review`
