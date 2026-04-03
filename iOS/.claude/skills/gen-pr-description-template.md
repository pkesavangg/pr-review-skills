---
name: gen-pr-description-template
description: Generate a formatted pull request description template based on branch changes, commit history, and files modified. This is an internal skill invoked by /pr-description and /raise-pr to eliminate template duplication. Use directly when you need to generate a standardized PR description for any branch or PR.
---

Generate a well-structured PR description from the provided context (branch changes, commits, files, or existing PR data).

## Inputs

The caller provides one of:
- **Local branch context**: git log, git diff, changed files list, ISSUE_ID from branch name
- **Existing PR context**: PR title, body, commits, changed files, ISSUE_ID

## Instructions

### Step 1: Extract Context

From the caller's input, identify:
1. **ISSUE_ID** — Extract from branch name (e.g., `MA-3591` from `MA-3591-enable-bpm-bluetooth-pairing`) or PR title
2. **Change type** — Categorize as Feature, Refactor, Bug fix, Test/Coverage, or Dependency update
3. **Changed files** — List from git diff or PR metadata
4. **Commit messages** — Extract key intent from commit log
5. **Test changes** — Determine if new tests were added (for Coverage section decision)

### Step 2: Build Summary Bullets

Use the **action + component + benefit** pattern to create 2-4 clear bullets explaining *why* and *what*, not which files were touched.

**Pattern examples by change type:**

#### Feature
✅ **Good**: 
- Enable Bluetooth pairing for scale setup to improve user onboarding
- Extend EntryService with device type detection for multi-device support

#### Refactor
✅ **Good**:
- Modularize DashboardStore subscription setup for better testability
- Separate entry, account, and product-type subscriptions to improve state isolation

#### Bug Fix
✅ **Good**:
- Fix race condition in account switching by adding synchronization guard
- Correct date boundary calculations to use calendar year instead of March offsets

#### Test Coverage
✅ **Good**:
- Add comprehensive test coverage for EntryService filtering by EntryType
- Implement integration tests for BPM dashboard data aggregation with real-time updates

### Step 3: Build Changes Table

List key files that were modified with brief explanations:

```markdown
| File | What changed |
|------|-------------|
| `meApp/Data/Services/EntryService.swift` | Added filterByEntryType() method for BPM vs WG separation |
| `meApp/Features/Dashboard/Stores/DashboardStore.swift` | Extracted subscription setup into separate methods |
```

**Guidelines:**
- Prioritize source files over test files
- For 20+ changed files: list key files explicitly, then use "9+ other files" notation
- Focus on files with most substantial changes
- Don't list every file — aim for 5-10 most important

### Step 4: Build Test Plan

Always include this checklist:

```markdown
## Test Plan
- [ ] Build passes with no errors or warnings
- [ ] SwiftLint passes with no errors
- [ ] Unit tests pass
- [ ] Manual smoke test on affected flows
```

Add feature-specific manual steps if relevant (e.g., "Pair a Bluetooth scale and verify BPM reads are correctly filtered").

### Step 5: Decide on Coverage Section

**Decision tree:**

```
Did this PR involve changing test files?
├─ NO → Omit Coverage section entirely
└─ YES → Are the changes NEW test implementations?
   ├─ YES (new @Test, new test files, new test cases) → INCLUDE Coverage section
   └─ NO (only fixes/mods to existing tests) → OMIT Coverage section
```

**If INCLUDED, format as:**

```markdown
## Coverage

| File | Coverage |
|------|----------|
| `meApp/Data/Services/EntryService.swift` | 88% |
| `meApp/Features/Dashboard/Stores/DashboardStore.swift` | 85% |
```

### Step 6: Add Jira Link

Always include:

```markdown
## Jira
[ISSUE-ID](https://greatergoods.atlassian.net/browse/ISSUE-ID)
```

Replace `ISSUE-ID` with the actual ID (e.g., `MA-3591`).

### Step 7: Assemble Final Description

Combine all sections into a single markdown block:

```markdown
## Summary
- [Bullet 1]
- [Bullet 2]
- [Bullet 3]

## Changes
| File | What changed |
|------|-------------|
| ... | ... |

## Test Plan
- [ ] Build passes with no errors or warnings
- [ ] SwiftLint passes with no errors
- [ ] Unit tests pass
- [ ] Manual smoke test on affected flows
[+ feature-specific steps if relevant]

## Coverage
[Only if new tests added]

## Jira
[ISSUE-ID](https://greatergoods.atlassian.net/browse/ISSUE-ID)
```

## Rules

- ✅ Replace `ISSUE-ID` in both display text and URL with actual ID
- ✅ Summary bullets explain *why* and *what*, not which files were touched
- ✅ Use consistent action verbs: "Add", "Extend", "Fix", "Improve", "Refactor", "Implement"
- ✅ Coverage section: INCLUDE only if NEW tests were added (not for test modifications)
- ❌ Do NOT include `Co-Authored-By` line or any attribution
- ❌ Do NOT call `gh pr create`, `gh pr edit`, or modify PR on GitHub — just generate the template

## Edge Cases

### Non-Standard Branch Names
1. Check commit messages for JIRA references
2. Check branch name for standalone numbers that could be issue IDs
3. If unclear, ask caller: "What Jira ticket should I reference? (e.g., MA-3591)"
4. Do NOT guess or invent an issue ID

### No Issue ID Found
- Ask the user: "What Jira ticket should I reference? (e.g., MA-3591)"
- Do not proceed without confirmed ISSUE_ID

### Very Large Diffs (20+ files)
- List key files explicitly
- Use "9+ other files" notation for remainder
- Focus Changes table on most substantial files

### Existing PR Body Provided
- If PR body is empty: generate full description from scratch
- If PR body exists: review and improve for clarity, detail, professionalism
- Keep structure but refine bullets, fix typos, add missing sections

## Output

Return the final PR description as a markdown code block, ready to copy-paste:

```markdown
[Complete PR description here]
```

**Do NOT push, create PRs, or modify GitHub** — just generate the template text.
