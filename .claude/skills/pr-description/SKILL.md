---
name: pr-description
description: Generate a structured pull request description (title + body) from a GitHub PR URL/number, the current local branch, or against a specified Jira issue. Use whenever the user asks to "write a PR description", "generate PR body", "draft PR", "pr-description for KITC-XXX", "describe this PR", "raise a PR against <JIRA-ID>", or pastes a github.com PR URL and asks for a summary. Includes the Jira issue ID in the title and body when detectable from the branch name, the user's request, or commit messages. Never adds Claude Code attribution / co-author footer. Works in any project (no project-specific assumptions).
---

# PR Description

Generate a clean, professional pull request title and description from local commits, a diff, or a GitHub PR. Output is ready to paste into GitHub or pass to `gh pr create`.

## When to invoke

Trigger this skill when the user:
- Asks to write/generate/draft a "PR description", "pull request description", or "PR body"
- Says "raise a PR against KITC-XXX" / "create PR for <JIRA-ID>"
- Pastes a `github.com/.../pull/N` URL and asks for a description or summary
- Says "describe this PR" / "what should the PR say" while on a feature branch
- Asks to commit changes and raise a PR (the commit step uses normal git rules; this skill produces the PR text)

## Inputs the skill must resolve

Before writing anything, determine:

1. **Source of changes** — pick the first that applies:
   - **GitHub PR URL or number** in the user's message → fetch via `gh pr view <id> --json title,body,headRefName,baseRefName,commits,files,url` and `gh pr diff <id>`.
   - **Branch override** ("against KITC-541", "for branch foo") → run `git log <base>..<branch>` and `git diff <base>...<branch>`.
   - **Current branch** (default) → run `git status`, `git log <base>..HEAD`, and `git diff <base>...HEAD`. Detect base via `gh repo view --json defaultBranchRef -q .defaultBranchRef.name` (fallback: `main`, then `master`).

2. **Jira ID** — the highest-priority match wins:
   1. Explicitly mentioned by the user (e.g. "against KITC-541")
   2. Branch name (regex: `[A-Z][A-Z0-9]+-\d+`)
   3. Any commit message subject line on the branch
   4. PR title or body (when given a GitHub PR)

   If none found, ask the user **once** for the Jira ID before generating output. Do not invent one.

3. **Repo + base branch** — needed for `gh pr create` and for diff bounds.

## Output format

Always emit **two clearly labeled blocks**: the title and the body. Both render-ready, no surrounding commentary.

### Title

`<JIRA-ID> <Short imperative summary>`

- Under 70 characters total when possible.
- Imperative mood ("Add X", "Fix Y", "Refactor Z") — not past tense, not gerund.
- No trailing period.
- Jira ID prefix is always present when one was resolved.

### Body (Markdown)

```
## Summary
- <1–3 bullets on WHY this change exists and the user-visible / system-visible outcome>

## Changes
- <bullet per logical change, grouped by area when helpful>
- <reference notable files as `path/to/File.ext`>

## Test plan
- [ ] <concrete verification step a reviewer can run>
- [ ] <edge case / regression check>

## Screenshots / Recording
- <screenshot for a static UI change, or a screen recording for an interactive/flow change — placeholder for the author to attach>

## Jira
- [<JIRA-ID>](https://<jira-host>/browse/<JIRA-ID>)
```

Rules for the body:
- **Summary** explains intent (the *why*), not a restatement of the diff.
- **Changes** is the *what* — group related edits, name files, but don't paste code.
- **Test plan** is a checklist a reviewer or QA can actually execute. Skip it only if the change is doc-only or a pure rename — say so explicitly in that case.
- **Screenshots / Recording** — include this section only when the change is **user-facing** (touches a view/screen, navigation, a visible string, layout, styling, an animation, or any flow the user interacts with). Leave a clear placeholder for the author to attach the media (you can't capture it for them) and tell them in your reply that it still needs a screenshot or recording. Pick the medium by the kind of change: a **screenshot** for a static UI change, a **screen recording** for an interactive or multi-step flow — and for a flow change (e.g. entry/onboarding), the recording should walk through that actual flow end-to-end. **Omit the section entirely** for non-visual PRs — docs-only, build/version-number bumps, CI/config-only, test-only, pure refactors, or backend/data-layer changes with no UI surface. (This mirrors the `/review-pr` reviewer check, which flags a missing screenshot/recording on user-facing PRs — adding the section here heads that comment off.)
- **Jira** section uses a Markdown link. If the Jira host is unknown, leave a plain `<JIRA-ID>` reference and note that the host should be filled in.
- If the user has set a Jira host preference in conversation or memory, use it. Otherwise default to `https://dmdbrands.atlassian.net/browse/<JIRA-ID>` only if the branch/repo clearly belongs to that org; otherwise use the bare ID.
- Omit any section that genuinely has no content rather than padding with "N/A".
- **Never** add: "🤖 Generated with [Claude Code]…", `Co-Authored-By: Claude…`, or any AI attribution footer. The user has explicitly opted out.

## Workflow

1. **Resolve inputs** (see above). Run the needed git / gh commands in parallel where independent.
2. **Read the diff** — actually look at it, not just the file list. The Summary must reflect what changed semantically, not just which files moved.
3. **Draft title and body** following the format above.
4. **Present to the user** as two fenced blocks (one for title, one for body) so they can copy or so you can pipe into `gh pr create`.
5. **If the user asked you to also raise the PR**, then after showing the draft:
   - Confirm the base branch.
   - Run `gh pr create --title "<title>" --body "$(cat <<'EOF' … EOF)"` using a heredoc for safe multiline.
   - Return the PR URL.
   - Follow standard git safety: do not push to remotes the user didn't ask about; do not force-push.
6. **If given an existing PR number** and the user wants the description updated, use `gh pr edit <id> --title … --body …` after showing the draft and getting confirmation (editing a published PR is user-visible — confirm first).

## Edge cases

- **No commits on branch yet** → tell the user there's nothing to describe; ask whether they want to commit first.
- **Branch has many unrelated commits** → still produce a single coherent description, but flag in your reply (outside the two output blocks) that the PR mixes concerns and offer to split.
- **Merge commits in range** → ignore them when summarizing; describe only the substantive changes.
- **No Jira ID anywhere** → ask once; if the user says "skip", omit the prefix and the Jira section (and note that omission in your reply).
- **Pure revert** → title `<JIRA-ID> Revert "<original subject>"`, body explains why.
- **Stacked/dependent PRs** → mention the dependency in Summary ("Depends on #N").

## Example

User: "commit the changes and raise a PR against KITC-541"

Skill output (after committing per normal git flow):

````
**Title**
```
KITC-541 Add permission monitoring system and Settings → Permissions screen
```

**Body**
```markdown
## Summary
- Surface live Bluetooth and Notification permission state to the user so denied permissions stop silently breaking device pairing.
- Add a dedicated Settings → Permissions screen with deep-link to iOS Settings when permissions are denied.

## Changes
- New `PermissionsMonitor` service that observes `CBCentralManager` and `UNUserNotificationCenter` authorization state and republishes via `Broadcaster<PermissionsSnapshot>`.
- `Features/Settings/Permissions/PermissionsView.swift` + ViewModel + route wired through `SettingsCoordinator`.
- Registered `PermissionsMonitor` in `ServiceRegistry`; injected into `AppSession` for global gating.

## Test plan
- [ ] Fresh install → grant Bluetooth, deny Notifications → Permissions screen reflects both states.
- [ ] Toggle Bluetooth in iOS Settings while app is foregrounded → screen updates without relaunch.
- [ ] Tap "Open Settings" row → deep-links to app's iOS Settings page.

## Jira
- [KITC-541](https://dmdbrands.atlassian.net/browse/KITC-541)
```
````

## Anti-patterns (don't do these)

- Don't restate the diff line-by-line in Summary.
- Don't write "This PR does X" — drop "This PR".
- Don't add emoji decorations to section headers unless the project's existing PRs do.
- Don't include the AI attribution footer — ever.
- Don't invent a Jira ID; ask if missing.
- Don't push or open the PR without explicit user instruction.
