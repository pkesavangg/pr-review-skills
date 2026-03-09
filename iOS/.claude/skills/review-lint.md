Check new/changed Swift code in the PR against the project's SwiftLint config and code style rules.

Inputs available: PR_META (number, title, branch), DIFF (full patch text), CHANGED_FILES (list), WORKTREE_PATH

## Instructions

### 1 — Read the Active SwiftLint Config

Before reviewing, always read the live config from the worktree so your review reflects the current rules:

```bash
cat {WORKTREE_PATH}/iOS/.swiftlint.yml
```

Extract and note:
- **opt_in_rules**: the full list of enabled opt-in rules
- **disabled_rules**: rules that are turned off (do not flag these)
- **Thresholds**: `line_length.warning`, `function_body_length.warning`, `type_body_length.warning`, `cyclomatic_complexity.warning`, `function_parameter_count.warning`, `nesting` levels
- **Naming bounds**: `type_name` and `identifier_name` min/max lengths and exclusions
- **Force operations severity**: whether `force_cast`, `force_try`, `force_unwrapping` are warnings or errors

Use these extracted values in Steps 2–4 instead of any hardcoded defaults.

---

### 2 — Run SwiftLint on Changed Files

From the repo root (`meApp-1/`), run SwiftLint scoped to only the files changed in this PR:

```bash
cd iOS && swiftlint lint \
  --config .swiftlint.yml \
  --reporter xcode \
  $(echo "{CHANGED_FILES}" | tr ' ' '\n' | grep '\.swift$' | xargs)
```

Collect all output. Separate **errors** (blocking) from **warnings** (non-blocking).

If SwiftLint is not available, skip to Step 3 and note "SwiftLint not available — manual review only".

---

### 3 — Manual Style Review of New Lines

Read the `+` lines in the diff. Check only the rules that are **active** in `.swiftlint.yml` (from Step 1). Skip any rule listed under `disabled_rules`.

For each active opt-in rule, apply the corresponding check:

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
| `contains_over_filter_is_empty` | `filter{}.isEmpty` → `!contains(where:)` |
| `first_where` | `filter{}.first` → `first(where:)` |
| `last_where` | `filter{}.last` → `last(where:)` |
| `redundant_nil_coalescing` | `x ?? nil` is redundant |
| `toggle_bool` | `x = !x` → `x.toggle()` |
| `operator_usage_whitespace` | `a+b` → `a + b` |
| `force_unwrapping` | Trailing `!` on optionals (severity from config) |

---

### 4 — Check Thresholds in New Code

Use the thresholds read from `.swiftlint.yml` in Step 1 (not hardcoded values).

For any new **function** added in the diff:
- Flag if body line count exceeds `function_body_length.warning`
- Flag if cyclomatic complexity (if/else/guard/switch/for/while/catch branches) exceeds `cyclomatic_complexity.warning`
- Flag if parameter count exceeds `function_parameter_count.warning`

For any new **type** (class/struct/enum):
- Flag if body exceeds `type_body_length.warning`

For any new **line**:
- Flag if character count exceeds `line_length.warning` (excluding URLs, function declarations, and comments if `ignores_*` flags are set in config)

For any new **identifier or type name**:
- Flag if shorter than `identifier_name.min_length.warning` or longer than `identifier_name.max_length.warning`
- Skip names listed under `identifier_name.excluded` or `type_name.excluded`

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
