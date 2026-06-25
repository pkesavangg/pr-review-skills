You are a full software development lifecycle agent. The user has given you a Jira issue ID (e.g. MOB-3316). Execute the following steps in order, pausing for user input where indicated.

The Jira issue ID is: $ARGUMENTS

> **Resuming mid-way:** If this command failed or was interrupted in a previous session, the user can tell you which step to resume from (e.g. "resume from step 5"). Skip completed steps and continue from the specified point. Gather any required context (branch name, changed files, Jira ID) from git and the working directory before resuming.

---

## STEP 1 — Fetch & Display Jira Task Details

Read and execute the skill at `.claude/skills/fetch-ticket.md` with `$ARGUMENTS` as the issue ID.

---

## STEP 1.7 — Read Design Assets (Conditional)

Check the ticket fetched in Step 1 for design assets. Run both checks independently:

**Check A — Figma URL:**
Scan the ticket description for a Figma URL (pattern: `https://www.figma.com/...`).
- If found: read and execute `.claude/skills/read-figma.md`, passing the full description. Display the Design Summary. Store as `{FIGMA_DESIGN_SUMMARY}`.
- If not found: set `{FIGMA_DESIGN_SUMMARY}` to empty.

**Check B — Image Attachments:**
Inspect `fields.attachment` from the issue data for image files (png, jpg, gif, webp).
- If found: read and execute `.claude/skills/read-jira-images.md`, passing the full issue data. Display the Image Summary. Store as `{IMAGE_SUMMARY}`.
- If not found: set `{IMAGE_SUMMARY}` to empty.

Both `{FIGMA_DESIGN_SUMMARY}` and `{IMAGE_SUMMARY}` are passed to Step 3 to enrich the PRD.

**If neither is found:** skip this step silently and continue.

---

## STEP 1.5 — Set Original Estimate

After fetching the ticket, check whether an Original Estimate is already set on the issue:
- Call `getJiraIssue` and inspect the `timeoriginalestimate` field (in seconds) or the `originalEstimate` from `timetracking`.

**If an estimate is already set:** display it to the user and proceed to Step 2.

**If no estimate is set:**
1. Ask the user:
   > "No original estimate is set on this ticket. How long do you estimate this will take? (e.g. `1h`, `2h 30m`, `30m`)"
2. Wait for the user's response.
3. Call `editJiraIssue` to update the `timetracking` field with the provided value as the `originalEstimate`.
4. Confirm: "Original estimate set to `{value}` on {ISSUE_ID}."

**Do not proceed to Step 2 until this step is complete.**

---

## STEP 2 — Create Git Branch

Read and execute the skill at `.claude/skills/create-branch.md` using the issue ID and summary from Step 1.

---

## STEP 3 — Explore, Generate PRD, and Plan

In a **single codebase exploration pass** (read at most 8 files total):
- Find source files related to the feature area: `rg -l "{keyword}" meApp -g '*.swift'`
- Read the 2–3 most relevant existing source files, tests, and patterns
- Identify what files need to change, be created, or deleted
- Identify risks, edge cases, and dependencies

Then, using that same exploration — **without re-reading any files**:

1. **Write the PRD** to `docs/plans/{ISSUE-ID}-{slugified-title}.md` using the template at `docs/templates/prd-template.md`. Populate every section from the Jira ticket and codebase findings. Use available design context to populate the UI/UX Requirements and Acceptance Criteria sections:
   - If `{FIGMA_DESIGN_SUMMARY}` is non-empty: use screen names, component names, text strings, and design tokens from it.
   - If `{IMAGE_SUMMARY}` is non-empty: use the UI layout, text content, and annotations from it.
   - If both are present: combine them — Figma provides structure/specs, images provide annotations/flow context.

2. **Present an interactive plan** to the user covering:
   - What the task requires (your understanding)
   - Proposed approach with alternatives if applicable
   - Files to **create**, **modify**, and **delete**
   - Test strategy
   - Risks or things to watch out for

Show the user the PRD path. **Ask the user for approval or feedback before proceeding.** Use EnterPlanMode for this step.

---

## STEP 4 — Implement

After plan approval, implement the changes using TodoWrite to track progress:
- Mark each task in_progress before starting it
- Mark each task completed immediately after finishing it
- Follow existing code conventions exactly
- Do not over-engineer — only implement what the task requires

Use scaffolding tools during implementation:
- **Any feature scaffold**: read and execute `.claude/skills/feature-slice.md` (handles simple top-level and nested archetypes)
- **New test file needed**: read and execute `.claude/skills/gen-test-file.md` to scaffold the file
- **1 new mock needed**: read and execute `.claude/skills/gen-mock-single.md`
- **2+ new mocks needed at once**: spawn the agent at `.claude/agents/gen-mock-batch.md` in the background while writing production code
- **New backend call or endpoint wiring**: read and execute `.claude/skills/add-endpoint.md`
- **New service / DI registration work**: read and execute `.claude/skills/wire-service.md`
- **SwiftData / Keychain / local storage changes**: read and execute `.claude/skills/storage-change.md`
- **Environment or build configuration changes**: read and execute `.claude/skills/config-change.md`

SwiftLint runs automatically via hook after each file edit — fix any violations surfaced before moving to the next file.

---

## STEP 5 — Build & Test Verification

Determine the task type and act accordingly:

**If the task involves production or unit-test code that should be covered by unit tests:**
Read and execute the skill at `.claude/skills/verify-tests.md` for **unit** tests with the list of changed source files and test files. Do not proceed until all changed files that are in coverage scope reach their layer threshold.

If any file is below threshold after the first run, spawn the agent at `.claude/agents/coverage-gap-finder.md` to identify exactly which methods/branches are uncovered. Add the missing tests, then re-run until all files pass.

**If the task involves writing or modifying UI tests:**
Read and execute the skill at `.claude/skills/verify-tests.md` for **UI** tests with the list of UI test files and screens/flows covered. Do not proceed until all files reach ≥ 85% coverage.

**For all other tasks (feature work, bug fixes, refactoring, etc.):**
Run a direct build verification from the repo root:
```bash
xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -configuration Dev \
  -destination 'generic/platform=iOS' \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```
Fix any build errors before proceeding.

Once verification passes, ask the user:
> "Lint is clean and verification passed. Please check the changes and confirm everything looks good before I commit."

**Do not proceed to Step 6 until the user explicitly confirms.**

> ⚠️ When the user confirms (e.g. "looks good", "proceed", "commit"), do NOT jump to Step 7.
> You MUST execute Step 6 (Self-Review) first — it is mandatory and non-skippable.

---

## STEP 6 — Self-Review

Read and execute the skill at `.claude/skills/self-review.md`.

**This step is mandatory. Execute it automatically after user confirmation in Step 5 — do not skip it even if the user says "commit" or "looks good".**

---

## STEP 7 — Commit

Read and execute the skill at `.claude/skills/commit.md` using the issue ID from Step 1.

---

## STEP 8 — Raise PR

Ask the user:
> "Which branch should I raise the PR against? (default: `main`)"

**Wait for the user's response before proceeding.** Store as `{BASE_BRANCH}` (default `main` if the user confirms or provides no input).

Then read and execute the skill at `.claude/skills/raise-pr.md` using the issue ID, summary from Step 1, and `{BASE_BRANCH}` as the target branch. Pass `{BASE_BRANCH}` so the skill uses it directly and does not ask again.

**After the PR URL is returned, immediately proceed to Step 9 — do not stop or wait for the user.**

---

## STEP 9 — Log Work

Read and execute the skill at `.claude/skills/log-work.md` using the issue ID from Step 1 and a summary of all work done.

**This step is mandatory and must be initiated automatically after Step 8. The skill will ask the user for the time spent — that is the only pause allowed here.**
