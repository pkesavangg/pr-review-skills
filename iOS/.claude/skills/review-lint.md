---
name: review-lint
description: Check new/changed Swift code against the project's SwiftLint config and code style rules. Use when reviewing a PR for lint issues, or when the user says "lint check", "check style", "run swiftlint". Also called automatically by /self-review and /review-pr.
---

Check new/changed Swift code against the project's SwiftLint config and code style rules.

Inputs available (from PR review context): PR_META, DIFF, CHANGED_FILES, WORKTREE_PATH.
When running standalone, derive these from git (see Step 0).

## Instructions

### 0 — Derive Inputs if Running Standalone

If CHANGED_FILES and DIFF were not provided by a caller:

```bash
# Files changed on this branch vs main
git diff --name-only $(git merge-base HEAD origin/main) HEAD
git diff --cached --name-only

# Full diff
git diff $(git merge-base HEAD origin/main) HEAD
git diff --cached
```

Set:
- **CHANGED_FILES** — union of `.swift` files from both outputs
- **DIFF** — combined patch text
- **WORKTREE_PATH** — `/Users/kesavan/meApp-1`

---

### 1 — Read the Active SwiftLint Config

```bash
cat {WORKTREE_PATH}/iOS/.swiftlint.yml
```

Extract and note:
- **opt_in_rules** — full list of enabled opt-in rules
- **disabled_rules** — rules that are off (do not flag these)
- **Thresholds** — `line_length.warning`, `function_body_length.warning`, `type_body_length.warning`, `cyclomatic_complexity.warning`, `function_parameter_count.warning`, nesting levels
- **Naming bounds** — `type_name` and `identifier_name` min/max lengths and exclusions
- **Force operations severity** — whether `force_cast`, `force_try`, `force_unwrapping` are warnings or errors

Use these values in Steps 2–4 instead of hardcoded defaults.

---

### 2 — Run SwiftLint on Changed Files

```bash
cd /Users/kesavan/meApp-1/iOS && swiftlint lint \
  --config .swiftlint.yml \
  --reporter xcode \
  $(echo "{CHANGED_FILES}" | tr ' ' '\n' | grep '\.swift$' | xargs)
```

Collect all output. Separate **errors** (blocking) from **warnings** (non-blocking).

If SwiftLint is not available, skip to Step 3 and note "SwiftLint not available — manual review only".

---

### 3 — Manual Style Review of New Lines

Read the `+` lines in the diff. Check only rules **active** in `.swiftlint.yml` (Step 1). Skip any rule under `disabled_rules`.

| Rule | What to check |
|------|--------------|
| `sorted_imports` | Imports in new files sorted alphabetically |
| `trailing_closure` | `foo(completion: { })` → `foo { }` |
| `closure_spacing` | `map{ }` → `map { }` |
| `unneeded_parentheses_in_closure_argument` | `{ (x) in }` → `{ x in }` |
| `multiline_arguments` | 3+ args over line limit → one arg per line |
| `multiline_parameters` | Same for function/init declarations |
| `vertical_parameter_alignment_on_call` | Multiline args must align vertically |
| `redundant_type_annotation` | `let x: String = "hi"` → `let x = "hi"` |
| `empty_count` | `count == 0` → `isEmpty` |
| `empty_string` | `== ""` → `isEmpty` |
| `contains_over_filter_count` | `filter{}.count > 0` → `contains(where:)` |
| `first_where` | `filter{}.first` → `first(where:)` |
| `last_where` | `filter{}.last` → `last(where:)` |
| `toggle_bool` | `x = !x` → `x.toggle()` |
| `operator_usage_whitespace` | `a+b` → `a + b` |
| `force_unwrapping` | Trailing `!` on optionals (severity from config) |

---

### 4 — Check Thresholds in New Code

Use thresholds from `.swiftlint.yml` (Step 1) — not hardcoded values.

For any new **function** in the diff:
- Body line count > `function_body_length.warning`
- Cyclomatic complexity (if/else/guard/switch/for/while/catch) > `cyclomatic_complexity.warning`
- Parameter count > `function_parameter_count.warning`

For any new **type** (class/struct/enum):
- Body > `type_body_length.warning`

For any new **line**:
- Character count > `line_length.warning` (respect `ignores_*` flags)

For any new **identifier or type name**:
- Shorter than min or longer than max (skip exclusion lists)

---

## Output

```
### Lint & Formatting Review

**SwiftLint errors:** {N}
**SwiftLint warnings:** {N}

SwiftLint Findings:
- {file}:{line}: [error/warning] {rule} — {message}

Manual Style Findings:
- {file}:{line}: {rule} — {description and suggested fix}

**Lint verdict:** PASS / WARNING / FAIL

Rules:
- FAIL = any SwiftLint errors present
- WARNING = SwiftLint warnings only, or manual style issues found
- PASS = no errors, no warnings, no manual style issues
```
