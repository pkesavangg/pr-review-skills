You are a full software development lifecycle agent. The user has given you a Jira issue ID (e.g. MA-3316). Execute the following steps in order, pausing for user input where indicated.

The Jira issue ID is: $ARGUMENTS

---

## STEP 1 — Fetch & Display Jira Task Details

Read and execute the skill at `.claude/skills/fetch-ticket.md` with `$ARGUMENTS` as the issue ID.

---

## STEP 2 — Create Git Branch

Read and execute the skill at `.claude/skills/create-branch.md` using the issue ID and summary from Step 1.

---

## STEP 3 — Generate PRD

Read and execute the skill at `.claude/skills/create-prd.md` with `$ARGUMENTS` as the issue ID.

This generates `iOS/docs/plans/{ISSUE-ID}-{slugified-title}.md` capturing:
- Problem statement + acceptance criteria from Jira
- Files to create / modify / delete
- Mocks and test files needed (with coverage thresholds)
- Risks and open questions
- Implementation checklist

Show the user the PRD path and key decisions captured. Continue automatically — no user confirmation needed at this step.

---

## STEP 4 — Deep Brainstorm & Interactive Planning

Thoroughly explore the codebase relevant to this task:
- Read existing files, patterns, tests, and conventions
- Identify what files need to change, be created, or deleted
- Identify risks, edge cases, and dependencies
- Think about the best approach

Then present a **detailed interactive plan** to the user covering:
- What the task requires (your understanding)
- Proposed approach with alternatives if applicable
- Files to **create**, **modify**, and **delete**
- Test strategy
- Risks or things to watch out for

**Ask the user for approval or feedback before proceeding.** Use EnterPlanMode for this step so the user can review and approve the plan.

---

## STEP 5 — Implement

After plan approval, implement the changes using TodoWrite to track progress:
- Mark each task in_progress before starting it
- Mark each task completed immediately after finishing it
- Follow existing code conventions exactly
- Do not over-engineer — only implement what the task requires

Use scaffolding tools during implementation:
- **New feature module**: read and execute `.claude/skills/new-feature.md` before writing any code
- **New test file needed**: read and execute `.claude/skills/gen-test-file.md` to scaffold the file
- **1 new mock needed**: read and execute `.claude/skills/gen-mock.md`
- **2+ new mocks needed at once**: spawn the agent at `.claude/agents/mock-generator.md` in the background while writing production code

SwiftLint runs automatically via hook after each file edit — fix any violations surfaced before moving to the next file.

---

## STEP 6 — Build & Test Verification

Determine the task type and act accordingly:

**If the task involves writing or modifying unit tests:**
Read and execute the skill at `.claude/skills/verify-unit-tests.md` with the list of test files and source files changed in this task. Do not proceed until all files reach their layer coverage threshold.

If any file is below threshold after the first run, spawn the agent at `.claude/agents/coverage-gap-finder.md` to identify exactly which methods/branches are uncovered. Add the missing tests, then re-run until all files pass.

**If the task involves writing or modifying UI tests:**
Read and execute the skill at `.claude/skills/verify-ui-tests.md` with the list of UI test files and screens/flows covered. Do not proceed until all files reach ≥ 85% coverage.

**For all other tasks (feature work, bug fixes, refactoring, etc.):**
Read and execute the skill at `.claude/skills/build-verify.md`. Fix any build errors before proceeding.

Once verification passes, ask the user:
> "Lint is clean and verification passed. Please check the changes and confirm everything looks good before I commit."

**Do not proceed to Step 7 until the user explicitly confirms.**

> ⚠️ When the user confirms (e.g. "looks good", "proceed", "commit"), do NOT jump to Step 8.
> You MUST execute Step 7 (Self-Review) first — it is mandatory and non-skippable.

---

## STEP 7 — Self-Review

Read and execute the skill at `.claude/skills/self-review.md`.

**This step is mandatory. Execute it automatically after user confirmation in Step 6 — do not skip it even if the user says "commit" or "looks good".**

---

## STEP 8 — Commit

Read and execute the skill at `.claude/skills/commit.md` using the issue ID from Step 1.

---

## STEP 9 — Raise PR

Read and execute the skill at `.claude/skills/raise-pr.md` using the issue ID and summary from Step 1.

**After the PR URL is returned, immediately proceed to Step 10 — do not stop or wait for the user.**

---

## STEP 10 — Log Work

Read and execute the skill at `.claude/skills/log-work.md` using the issue ID from Step 1 and a summary of all work done.

**This step is mandatory and must be initiated automatically after Step 9. The skill will ask the user for the time spent — that is the only pause allowed here.**
