---
name: fix-pr-comments
description: Given a GitHub PR URL, fetches all review comments, triages each one for actionability, and auto-applies the clear/concrete fixes — then reports what was changed and what needs human discussion. Use this skill whenever the user says "fix PR comments", "address review feedback", "apply reviewer suggestions", "respond to PR feedback", "fix the review comments", "act on code review", or "resolve PR comments". Invoke proactively when the user shares a GitHub PR URL alongside any intent to act on feedback.
---

# Fix PR Comments

Fetch all review comments on a pull request, triage them into actionable vs. deferred, apply safe auto-fixes, and produce a clear summary.

---

## Step 1 — Get the PR URL

If the user did not provide a PR URL, ask:

> "Please paste the GitHub PR URL (e.g. `https://github.com/owner/repo/pull/123`)."

Parse the URL to extract:
- `OWNER` — GitHub org or username
- `REPO` — repository name
- `PR_NUMBER` — pull request number

Validate the format before proceeding. If the URL doesn't match `github.com/{owner}/{repo}/pull/{number}`, tell the user and stop.

---

## Step 2 — Fetch All Review Comments

Run both commands in parallel:

```bash
# Inline review comments (attached to specific lines/hunks)
gh api repos/{OWNER}/{REPO}/pulls/{PR_NUMBER}/comments \
  --paginate \
  --jq '.[] | {id, author: .user.login, body, path, line, diff_hunk, in_reply_to_id}'

# General PR conversation comments
gh api repos/{OWNER}/{REPO}/issues/{PR_NUMBER}/comments \
  --paginate \
  --jq '.[] | {id, author: .user.login, body, created_at}'
```

For each comment record:
- `id` — unique identifier
- `author` — who wrote it
- `body` — comment text
- `path` — file path (inline comments only)
- `line` — line number (inline comments only)
- `diff_hunk` — surrounding diff context (inline comments only)

**Large PRs:** If there are more than 50 comments, process them in batches of 25. Report batch progress to the user.

---

## Step 3 — Triage Every Comment

Classify each comment into exactly one category. The goal is to be conservative — only auto-fix what is unambiguously safe.

### Auto-fix
The comment describes a clear, concrete code change with no architectural ambiguity:
- Typo or naming fix (variable, function, type name)
- Missing or wrong access modifier (`private`, `internal`, `public`)
- Redundant code or unnecessary `import`
- Style violation (trailing whitespace, brace placement, blank lines)
- Missing or incorrect inline comment / doc comment
- Obvious missing `nil` check or guard on non-critical path
- Formatting / indentation issue not caught by SwiftLint

**Never auto-fix, even if the comment sounds simple:**
- Changes to a `protocol` definition or its method signatures
- Changes to business logic, algorithms, or conditional branching
- Changes to test assertions, test setup, or test data
- Comments asking for a complete rewrite of a function or type
- Comments about removing or replacing a dependency

### Needs discussion
Route to "Needs discussion" when the fix requires judgment or could have side effects:
- Architectural suggestions ("consider using X pattern instead")
- Performance improvements with trade-offs
- API design questions
- Comments that are subjective ("I'd prefer...", "I think...", "Maybe...")
- Any comment that proposes changing a public interface
- Comments where the fix touches business logic or test assertions
- Ambiguous or contradictory instructions

### Skip
Ignore silently:
- Comments from bots: authors matching `*[bot]`, `github-actions`, `dependabot`, `renovate`, `codecov`
- Questions without a request to change code ("Why is this here?", "What does this do?")
- Praise or acknowledgment ("Looks good!", "+1", "LGTM")
- Already-resolved threads (check if the comment thread is marked resolved via `gh api`)
- Duplicate comments making the same point as another comment on the same line

---

## Step 4 — Apply Auto-fixes

For each "Auto-fix" comment, in file order (group changes to the same file together):

### 4a. Read and apply the fix

1. Read the target file.
2. Locate the relevant line using `path` + `line` from the comment. Use `diff_hunk` as additional context if the line number is ambiguous.
3. Apply the minimal change that satisfies the reviewer's request.
4. Do not touch any other lines in the file.

### 4b. Run post-change-guard on the edited file

After all edits to a given file are applied, run SwiftLint on that file only:

```bash
swiftlint lint --path {FILE_PATH} --fix
```

If the guard identifies a new violation introduced by the fix, resolve it immediately.

### 4c. Build check on vital files

If the edited file is in any of these directories, run a build check after applying all fixes in that file:

```
Core/DI/
Core/Services/ServiceRegistry.swift
Domain/Repositories/*Protocol.swift
Domain/Services/*Protocol.swift
Data/Services/
Data/API/
Core/Network/
Domain/Models/DB/
```

Build command:
```bash
cd iOS && xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'generic/platform=iOS' \
  -configuration Debug \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO \
  2>&1 | tail -20
```

### 4d. Revert on build failure

If the build fails after applying a fix:
1. Revert the change to that file (`git checkout -- {FILE_PATH}`).
2. Reclassify the comment as "Needs discussion" with a note: "Build failed after applying fix — manual review required."
3. Continue processing remaining auto-fix comments.

---

## Step 5 — Report Results

Output a summary table followed by a deferred-items explanation.

### Summary table

| Author | File | Line | Category | Action Taken |
|--------|------|------|----------|--------------|
| reviewer | `path/to/file.swift` | 42 | Auto-fix | Applied: renamed `foo` → `fooBar` |
| reviewer | `path/to/file.swift` | 78 | Auto-fix (reverted) | Build failed — reclassified |
| reviewer | *(general)* | — | Needs discussion | Proposes changing public protocol signature |
| reviewer | `path/to/file.swift` | 15 | Skip | Bot comment (github-actions[bot]) |

### Deferred items

For each "Needs discussion" item, give one sentence explaining why it was deferred:

> **`{author}` on `{path}:{line}`** — Deferred: comment proposes changing the `AccountServiceProtocol` method signature, which affects DI registration and all conforming mocks.

### Footer

```
Auto-fixed: N comments across M files
Needs discussion: P comments (listed above)
Skipped: Q comments (bots, praise, resolved)

No changes have been committed. Review the diff with `git diff` before committing.
```

---

## Guardrails

- **Never commit.** Leave all changes as unstaged working-tree edits for the developer to review.
- **Never change public protocols.** If a comment touches a `*Protocol.swift` file, it's always "Needs discussion".
- **Never change test assertions.** Comments in `meAppTests/` or `meAppUITests/` that request assertion changes are always "Needs discussion".
- **Never change business logic.** If unsure whether something is "business logic", defer it.
- **Preserve CLAUDE.md conventions.** All edits must follow the iOS architecture, naming conventions, and style rules defined in `CLAUDE.md`.
- **One comment, one fix.** Don't bundle multiple comment resolutions into one edit. Apply them individually so each change is traceable.
