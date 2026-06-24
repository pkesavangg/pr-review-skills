---
name: swiftlint
description: Run SwiftLint on changed files, auto-fix correctable violations, and manually fix remaining errors. Use when the user says "fix lint", "run swiftlint", "lint fix", "fix lint issues", "clean up lint", "swiftlint errors", or when lint violations are blocking a commit.
---

Run SwiftLint, auto-fix what it can, and manually fix the rest.

The scope is: $ARGUMENTS

## Background

This project uses SwiftLint with a custom `.swiftlint.yml` that includes:
- **HIPAA custom rules** (errors): `no_print_or_nslog`, `no_direct_userdefaults`, `no_hardcoded_credentials`
- **Force operations** (errors): `force_cast`, `force_try`, `force_unwrapping`
- **Opt-in style rules**: `sorted_imports`, `trailing_closure`, `empty_count`, `toggle_bool`, `redundant_type_annotation`, etc.
- **Thresholds**: line 150/200, function body 50/100, type body 500/1000, cyclomatic complexity 10/20, params 5/8

## Instructions

### 1 — Determine Scope

If `$ARGUMENTS` specifies files or a feature, use those.

Otherwise, lint all Swift files changed on this branch:

```bash
git diff --name-only $(git merge-base HEAD origin/main) HEAD -- '*.swift' | grep -v DerivedData | grep -v SourcePackages
```

Also include any unstaged/staged changes:
```bash
git diff --name-only -- '*.swift'
git diff --cached --name-only -- '*.swift'
```

Combine into a deduplicated file list. Store as `{LINT_FILES}`.

If no Swift files changed, report "No Swift files to lint" and exit.

---

### 2 — Run SwiftLint Auto-Fix

Run the auto-corrector first — it fixes ~50 correctable rules automatically:

```bash
swiftlint lint --fix --config .swiftlint.yml {LINT_FILES}
```

**Auto-correctable rules include:**
`sorted_imports`, `trailing_closure`, `closure_spacing`, `operator_usage_whitespace`, `redundant_type_annotation`, `redundant_nil_coalescing`, `toggle_bool`, `unneeded_parentheses_in_closure_argument`, `empty_count`, `trailing_comma`, `colon`, `comma`, `vertical_whitespace`, `duplicate_imports`, `trailing_newline`, and more.

After auto-fix, report what was corrected:
```bash
git diff --stat
```

---

### 3 — Run SwiftLint Lint (Post Auto-Fix)

Check what remains after auto-fix:

```bash
swiftlint lint --config .swiftlint.yml --reporter xcode --quiet {LINT_FILES} 2>&1
```

Separate the output into:
- **Errors** (blocking — must fix)
- **Warnings** (non-blocking — should fix)

If no violations remain, skip to Step 6.

---

### 4 — Manually Fix Remaining Errors

For each remaining **error**, apply the appropriate fix:

#### HIPAA Custom Rules (cannot be auto-fixed)

| Rule | Violation | Fix |
|------|-----------|-----|
| `no_print_or_nslog` | `print(...)` or `NSLog(...)` | Replace with `logger.log(level: .debug, tag: tag, message: "...")` — see `/analytics` |
| `no_direct_userdefaults` | `UserDefaults.standard` | Replace with `KvStorageService` for non-sensitive data or `KeychainService` for sensitive data |
| `no_hardcoded_credentials` | `apiKey = "abc123"` | Move to environment config (`xcconfig`) or Keychain |

#### Force Operations (cannot be auto-fixed)

| Rule | Violation | Fix |
|------|-----------|-----|
| `force_unwrapping` | `value!` | Use `guard let value = value else { return }` or `if let` |
| `force_cast` | `as! Type` | Use `as? Type` with `guard`/`if let` |
| `force_try` | `try!` | Use `do { try ... } catch { ... }` or `try?` |

#### Threshold Violations (cannot be auto-fixed)

| Rule | Threshold | Fix |
|------|-----------|-----|
| `function_body_length` | >50 lines warning, >100 error | Extract helper methods |
| `type_body_length` | >500 lines warning, >1000 error | Extract extensions or split responsibilities |
| `file_length` | >500 lines warning, >1000 error | Split into multiple files |
| `cyclomatic_complexity` | >10 warning, >20 error | Simplify control flow — extract conditions, use early returns, reduce nesting |
| `function_parameter_count` | >5 warning, >8 error | Group parameters into a config struct or use builder pattern |
| `line_length` | >150 warning, >200 error | Break long lines — multiline function calls, extract variables |

#### Naming Violations (cannot be auto-fixed)

| Rule | Fix |
|------|-----|
| `type_name` too short (<3) or too long (>50) | Rename to a descriptive name within bounds |
| `identifier_name` too short (<2) or too long (>50) | Rename — use `excluded` list in config only for standard abbreviations |

---

### 5 — Fix Remaining Warnings

For each remaining **warning**, fix if straightforward:

| Rule | Quick Fix |
|------|-----------|
| `empty_count` | `array.count == 0` → `array.isEmpty` |
| `empty_string` | `str == ""` → `str.isEmpty` |
| `contains_over_filter_count` | `filter{}.count > 0` → `contains(where:)` |
| `first_where` | `filter{}.first` → `first(where:)` |
| `last_where` | `filter{}.last` → `last(where:)` |
| `multiline_arguments` | Put each argument on its own line |
| `multiline_parameters` | Put each parameter on its own line |
| `vertical_parameter_alignment_on_call` | Align parameters vertically |
| `static_operator` | Move operator overload to `static func` |
| `sorted_first_last` | Use `min()` / `max()` instead of `sorted().first` / `sorted().last` |

---

### 6 — Verify Clean

Run SwiftLint one final time to confirm zero violations:

```bash
swiftlint lint --config .swiftlint.yml --reporter xcode --quiet {LINT_FILES} 2>&1
```

If any errors remain, go back to Step 4.

---

### 7 — Report

```
### SwiftLint Results

**Scope:** {N} files linted

**Auto-fixed (Step 2):**
- {rule}: {count} violations corrected
- Total auto-fixes: {N}

**Manually fixed (Steps 4–5):**
- {file}:{line} {rule} — {what was changed}
- Total manual fixes: {N}

**Remaining warnings (acceptable):**
- {list, or "None"}

**Final status:** ✅ Clean / ⚠️ Warnings only / ❌ Errors remain

Files modified: {list}
```

If HIPAA rules were violated (`no_print_or_nslog`, `no_direct_userdefaults`, `no_hardcoded_credentials`), flag prominently — these are compliance-critical and must be fixed before any commit.
