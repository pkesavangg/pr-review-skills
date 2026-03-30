---
name: pr-description
description: Write a pull request description for the current branch. Use when the user says "write a PR description", "draft a PR description", "write a PR body", "describe this PR", or "what should I put in the PR description".
---

Generate a well-structured PR description based on the current branch changes without pushing or creating a PR.

Inputs available: current branch name (contains ISSUE_ID), git diff vs main, list of changed files, recent commit messages.

## Instructions

1. Gather context in parallel:
   ```
   git log main..HEAD --oneline
   git diff main...HEAD --stat
   git diff main...HEAD
   ```

2. Extract the ISSUE_ID from the branch name (e.g. `MA-3591` from `MA-3591-enable-bpm-bluetooth-pairing`).

3. Read any changed Swift files that are unclear from the diff alone to understand intent.

4. Draft the PR description using this format:

   ```markdown
   ## Summary
   - <clear bullet points explaining what changed and why — focus on intent, not file names>

   ## Changes
   | File | What changed |
   |------|-------------|
   | `path/to/file.swift` | <brief description> |

   ## Test Plan
   - [ ] Build passes with no errors or warnings
   - [ ] SwiftLint passes with no errors
   - [ ] Unit tests pass
   - [ ] Manual smoke test on affected flows
   <add feature-specific manual steps if relevant>

   ## Coverage
   _(Include this section only if the task involves unit or UI tests)_

   | File | Coverage |
   |------|----------|
   | `path/to/SourceFile.swift` | X% |

   ## Jira
   [ISSUE-ID](https://greatergoods.atlassian.net/browse/ISSUE-ID)
   ```

5. Rules:
   - Replace `ISSUE-ID` in both display text and URL with the actual ID (e.g. `MA-3591`).
   - Summary bullets explain the *why* and *what*, not which files were touched.
   - Coverage section is omitted entirely for non-test tasks.
   - Do NOT include a `Co-Authored-By` line or any attribution.
   - Do NOT push the branch or call `gh pr create` — output the description text only.

6. Output the final description in a markdown code block so the user can copy it directly.
