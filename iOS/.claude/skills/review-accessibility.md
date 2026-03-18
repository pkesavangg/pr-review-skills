---
name: review-accessibility
description: Check that new or modified SwiftUI views have accessibility labels, VoiceOver support, and correct accessibilityHidden usage on decorative elements. Use when the user says "accessibility review", "check VoiceOver", "a11y check", or when called from the review-pr pipeline.
---

Review new and modified SwiftUI views for accessibility compliance.

Inputs available: CHANGED_FILES (list of modified .swift files), WORKTREE_PATH, DIFF

## Instructions

### 1 — Identify View Files in Scope

From CHANGED_FILES, filter to only SwiftUI view files:
- Files under `Views/Screens/` or `Views/Components/`
- Files ending in `View.swift`, `Screen.swift`, or `Component.swift`

Skip non-view files (stores, services, repositories, models).

---

### 2 — Establish Baseline

Before reviewing changed files, read one existing well-structured view in the codebase to calibrate expectations for this project's accessibility patterns:

```bash
rg -l "accessibilityLabel\|accessibilityIdentifier" meApp/Features -g '*Screen.swift' | head -3
```

Read the first result that has both `accessibilityLabel` and `accessibilityIdentifier` usage. Use its patterns as the baseline for what "good" looks like in this project.

---

### 3 — Check Each View

For each view file, read it and check:

**Interactive elements (buttons, taps, gestures):**
- Do all `Button` and `.onTapGesture` elements have a descriptive accessibility label?
  - Via `.accessibilityLabel("...")` or inheriting from visible `Text("...")` content
- Do icon-only buttons (e.g. `Image(systemName:)`) have explicit `.accessibilityLabel`?

**Images and icons:**
- Are purely decorative images marked `.accessibilityHidden(true)`?
- Do informational images have `.accessibilityLabel`?

**Form inputs:**
- Do `TextField` and `SecureField` elements have `.accessibilityLabel` or a visible associated label?

**Navigation elements:**
- Are custom navigation bar items and back buttons accessible?

**Dynamic content:**
- If content changes based on state (loading, error, empty), are all states accessible?

**Minimum bar:** Every user-interactive element must be reachable and describable by VoiceOver.

---

### 4 — Check accessibilityIdentifier for UI Test Targeting

If the view is tested (or will be tested) by `meAppUITests`:
- Confirm interactive elements use `.accessibilityIdentifier("...")` so tests can target them
- Flag any interactive elements in tested views that lack identifiers

---

### 5 — Output

```
### Accessibility Review

| File | Interactive Labels | Decorative Hidden | UI Test Identifiers | Issues |
|------|--------------------|-------------------|---------------------|--------|
| …    | ✅ / ⚠️ / ❌      | ✅ / ⚠️ / N/A    | ✅ / ⚠️ / N/A      | …      |

**Verdict:** PASS / NEEDS CHANGES

Findings:
- [file:line] Description of accessibility issue and recommended fix
```

- **FAIL** if any interactive element is completely inaccessible (no label, not reachable by VoiceOver)
- **WARNING** if decorative elements lack `.accessibilityHidden(true)`, or UI-tested views lack identifiers
