---
name: review-ui-standards
description: Audit new/changed SwiftUI views against the project's UI and theme standards — theme token usage (colors, spacing, radii, typography), hardcoded magic numbers, accessibility labels, reusable component patterns, and SwiftUI best practices. Use when reviewing a PR for UI quality, or when the user says "UI review", "check theme usage", "design standards review". Also called automatically by /review-pr.
---

Audit new and changed SwiftUI views against the project's UI and theme standards.

Inputs available: PR_META (number, title, branch), DIFF (full patch text), CHANGED_FILES (list), WORKTREE_PATH

## Instructions

### 0 — Identify View Files in Scope

From CHANGED_FILES, filter to SwiftUI view files:
- Files under `Views/Screens/` or `Views/Components/`
- Files ending in `View.swift`, `Screen.swift`, `Sheet.swift`, `Card.swift`, `Section.swift`
- Domain model files that define UI colors (e.g. `Color(...)` constructors)

Skip services, repositories, stores (unless they contain UI color definitions), tests, and pure data models.

---

### 1 — Read Theme Tokens

Read the project's theme token files to know what's available:

```bash
find {WORKTREE_PATH}/meApp/Theme -name '*.swift' | head -10
```

Read the key files to extract available:
- **Spacing tokens** (e.g. `.spacingXS`, `.spacingSM`, `.spacingMD`, `.spacingLG`, `.spacingXL`, `.spacingXSM`)
- **Border radius tokens** (e.g. `.radiusXS`, `.radiusSM`, `.radiusMD`, `.radiusLG`)
- **Color tokens** (via `@Environment(\.appTheme)` palette properties)
- **Typography tokens** (e.g. `.fontOpenSans(.heading1)`, `.body2`, `.subHeading1`)

---

### 2 — Theme Token Usage (Colors, Spacing, Radii)

For each view file, scan new `+` lines for:

**Hardcoded colors:**
- `Color.black`, `Color.white`, `Color.gray`, `Color.red`, etc. (should use `theme.*` tokens)
- `Color(red:green:blue:)` or `Color(hex:)` constructors (should be in asset catalog or theme)
- `Color("...")` string-based asset lookups (should use typed theme tokens)
- Exception: `.clear`, `.primary`, `.secondary` system semantic colors are acceptable

**Hardcoded corner radii:**
- `.cornerRadius(N)` where N is a magic number → should use `.radiusXS`, `.radiusSM`, `.radiusMD`, `.radiusLG`
- `RoundedRectangle(cornerRadius: N)` with magic numbers

**Hardcoded spacing/padding:**
- `.padding(N)`, `.padding(.horizontal, N)`, `.padding(.vertical, N)` where N is a raw number
- `spacing: N` in `VStack`, `HStack`, `LazyVGrid` etc. where N is a raw number
- Should use `.spacingXS` (8), `.spacingXSM` (12), `.spacingSM` (16), `.spacingMD` (24), `.spacingLG` (32), `.spacingXL` (40)
- Exception: `spacing: 0` / `spacing: .zero` is acceptable

**Hardcoded frame sizes:**
- `.frame(width: N)`, `.frame(height: N)` where N is a magic number with no named constant
- For fixed layout dimensions, values should be extracted to a named constant in a `Constants` or `Layout` enum

**Hardcoded font sizes:**
- `.font(.system(size: N))` — should use `.fontOpenSans()` typography tokens
- Direct `Font.custom(_, size:)` — should use typography tokens

Flag each magic number with the file, line, value, and recommended token/constant.

**Severity:**
- Hardcoded colors in production views → **FAIL**
- >5 magic numbers in a single file → **FAIL**
- 1-5 magic numbers → **WARNING**
- Inline `Color(red:green:blue:)` in domain models with TODO → **WARNING**

---

### 3 — Accessibility

For each view file:

**Interactive elements:**
- Every `Button`, `.onTapGesture`, `NavigationLink` must have a descriptive `.accessibilityLabel()`
  - Buttons with only `Text("...")` content inherit the label — acceptable
  - Icon-only buttons (e.g. `Image(systemName:)`) MUST have explicit `.accessibilityLabel()`

**Decorative elements:**
- Purely decorative `Image` or icon elements should have `.accessibilityHidden(true)`

**Combined elements:**
- Complex composite views (cards with multiple text elements) should use `.accessibilityElement(children: .combine)` with a descriptive `.accessibilityLabel()`

**Charts and graphs:**
- Chart views should have `.accessibilityElement(children: .ignore)` or `.accessibilityElement(children: .combine)` with a text summary

Flag missing accessibility on interactive elements as **FAIL**.
Flag missing accessibility on complex cards/composites as **WARNING**.
Flag missing decorative hiding as **WARNING**.

---

### 4 — Reusable Components

Check if new view files duplicate patterns that already exist:

- Search `{WORKTREE_PATH}/meApp/Features/Common/Views/Components/` for similar components
- Flag identical or near-identical view code appearing in 2+ files (should be extracted to a shared component)
- Flag chart/graph rendering duplicated outside `BaseGraphView` or shared chart utilities

**Severity:** **WARNING** for duplicated patterns.

---

### 5 — SwiftUI Best Practices

Check for common SwiftUI issues:

- **Nested vertical ScrollViews** — breaks gesture handling → **FAIL**
- **`UIScreen.main.bounds`** — deprecated in iOS 16+; use `GeometryReader` → **WARNING**
- **Object creation in `body`** — expensive objects created on every render → **WARNING**
- **`@ObservedObject` for owned state** — should be `@StateObject` if the view creates it → **FAIL**
- **`@StateObject` for injected state** — should be `@ObservedObject` if passed from parent → **WARNING**

---

### 6 — Hardcoded Strings in Views

Scan view files for inline string literals in `Text("...")` calls that should be in a `Strings` file.

- Acceptable: format specifiers like `"\(value)"`, empty strings `""`, SF Symbol names
- Not acceptable: user-facing labels, descriptions, titles, button text, accessibility labels with prose

Flag as **WARNING** per instance, **FAIL** if >3 in a single file.

---

## Output

```
### UI Standards Review

| Category | Status | Notes |
|----------|--------|-------|
| Theme Tokens (Colors) | PASS / WARNING / FAIL | … |
| Theme Tokens (Spacing/Radii) | PASS / WARNING / FAIL | … |
| Theme Tokens (Typography) | PASS / WARNING / FAIL | … |
| Accessibility | PASS / WARNING / FAIL | … |
| Reusable Components | PASS / WARNING / N/A | … |
| SwiftUI Best Practices | PASS / WARNING / FAIL | … |
| Hardcoded Strings | PASS / WARNING / FAIL | … |

**UI standards verdict:** PASS / WARNING / FAIL

Findings:
- [file:line] Value `N` → recommended token/constant and fix
```

Overall verdict rules:
- Any **FAIL** → UI standards verdict = FAIL
- Any **WARNING**, no FAIL → UI standards verdict = WARNING
- All **PASS** → UI standards verdict = PASS
