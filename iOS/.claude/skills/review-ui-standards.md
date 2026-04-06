---
name: review-ui-standards
description: Audit new/changed SwiftUI views against the project's UI and theme standards — theme token usage (colors, spacing, radii, typography), accessibility labels, reusable components, SwiftUI best practices, state management correctness (ForEach identity, @State/@StateObject), deprecated API patterns (iOS 16+ modernization), and iOS 26+ readiness. Use when reviewing a PR for UI quality, or when the user says "UI review", "check theme usage", "design standards review". Also called automatically by /review-pr.
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

### 5 — SwiftUI Best Practices & State Management

Check for common SwiftUI issues:

#### 5a — View Composition & Performance

- **Nested vertical ScrollViews** — breaks gesture handling → **FAIL**
- **`UIScreen.main.bounds`** — deprecated in iOS 16+; use `GeometryReader` → **WARNING**
- **Object creation in `body`** — expensive objects created on every render → **WARNING**
- **Heavy computation in view body** — logic that should be in computed properties → **WARNING**
- **Multiple expensive operations chained** — suggest extracting to subviews → **WARNING**

#### 5b — ForEach Identity (Critical for List Diffing)

Check all `ForEach` loops in new `+` lines:

- **`ForEach(items, id: \.self)` with mutable/non-Hashable content** → **FAIL**
  - Only acceptable if items are immutable, Hashable value types (String, Int, UUID)
  - Flag if items array is mutated or if type is custom non-Hashable class
- **`ForEach(items.indices)` for dynamic content** → **FAIL**
  - `.indices` breaks SwiftUI diffing when array content changes
  - Recommend: explicit `.id()` on items (e.g., `ForEach(items, id: \.id)`)
- **Missing `.id()` on list items** in dynamic lists → **WARNING**
  - If items lack a stable identity property, add `.id(item.uuid)` or similar
- **Constant number of views per ForEach element** — verify each element renders fixed number of children (variable children per element → **WARNING**)

#### 5c — State Management Correctness

- **`@State` properties not marked `private`** → **WARNING**
  - All `@State` should be `private` unless they're Binding sources
- **`@StateObject` for injected dependencies** → **FAIL**
  - Should be `@ObservedObject` if passed from parent
  - `@StateObject` only for view-owned objects created in `init`
- **`@ObservedObject` for owned state** → **FAIL**
  - Should be `@StateObject` if the view creates the object
  - `@ObservedObject` only for injected dependencies
- **`@Binding` without parent state update** → **WARNING**
  - Bindings should only exist when child modifies parent state
  - Misuse indicates unnecessary coupling
- **iOS 17+ `@Observable` adoption** → **INFO** (not required, but modern)
  - For new code: prefer `@Observable` over `@ObservedObject`
  - Requires `@Bindable` for Binding sources (iOS 17+)
  - Check: `@Observable` classes paired with `@Bindable` when binding needed

---

### 6 — Hardcoded Strings in Views

Scan view files for inline string literals in `Text("...")` calls that should be in a `Strings` file.

- Acceptable: format specifiers like `"\(value)"`, empty strings `""`, SF Symbol names
- Not acceptable: user-facing labels, descriptions, titles, button text, accessibility labels with prose

Flag as **WARNING** per instance, **FAIL** if >3 in a single file.

---

### 7 — Deprecated API Detection

Check new `+` lines against modern iOS 16+ / 17+ API patterns. Consult latest Apple SwiftUI documentation and this project's patterns.

**Common iOS 16+ modernizations:**
- `.onAppear { Task { ... } }` — **iOS 17+ preferred** over bare `.onAppear { ... }`
  - Flag: `.onAppear` doing async work without `Task` wrapper → **WARNING**
- `Text(.init(item.date, format: .date.abbreviated))` — prefer `.formatted()` → **WARNING**
- `Image(uiImage:)` with `UIImage(data:)` — consider `AsyncImage` for network loads → **WARNING**
- `.listStyle(.insetGrouped)` — ensure `ListStyle` is appropriate for iOS version → **INFO**

**Deprecated patterns (iOS 16+ replacements):**
- `UIActivityViewController` → suggest `ShareLink` (iOS 16+) → **WARNING**
- `.onReceive(timer)` — consider `Timer.publish().autoconnect()` + `.onReceive()` OR use `Task` with `Clock.sleep()` → **WARNING**
- `.navigationBarBackButtonHidden(true)` without custom back button → **WARNING** (accessibility risk)

**iOS 17+ only:**
- `@Observable` classes without `@Bindable` when creating bindings → **WARNING**
- Phase-based animations (`.phaseAnimation` vs `.animation`) → **INFO** (good pattern, not required)
- `matchedGeometryEffect` without transition context → **WARNING** (performance issue)

Flag deprecated patterns as **WARNING**. No **FAIL** (not breaking, just outdated).

---

### 8 — iOS 26+ Patterns (Future-Ready)

For views targeting iOS 26+, check for adoption of new patterns:

**Liquid Glass integration:**
- Views using `.background()` with complex gradients should consider `.liquidGlass()` (iOS 26+) if design allows
- Fallback with `#available(iOS 26, *)` required
- Flag: Complex glass morphism without Liquid Glass → **INFO** (optimization opportunity)

**Chart3D patterns (iOS 26+):**
- If charts are used, verify Swift Charts 3D patterns are adopted
- Must gate with `#available(iOS 26, *)` 
- Flag: 3D chart data without Chart3D support → **INFO**

**Animation improvements (iOS 26+):**
- `@Animatable` macro for custom animatable types → **INFO** (convenience)
- Keyframe animations (iOS 17+) for sequential effects → **INFO** (consider vs explicit animation)

**Modern layout system (iOS 26+):**
- New layout primitives (if available) offer better performance
- Flag: Complex `GeometryReader` + manual layout → **INFO** (check for iOS 26+ alternative)

**Note:** iOS 26+ is future-ready optimization only. Not required; flag as **INFO** suggestions, not blockers.

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
| ForEach Identity (Diffing) | PASS / WARNING / FAIL | … |
| State Management Correctness | PASS / WARNING / FAIL | … |
| Deprecated API Usage | PASS / WARNING | … |
| Hardcoded Strings | PASS / WARNING / FAIL | … |
| iOS 26+ Readiness | PASS / INFO | … |

**UI standards verdict:** PASS / WARNING / FAIL

Findings:
- [file:line] Description of issue and recommended fix

### Critical Issues Found
- [file:line] ForEach identity error or state management bug (if any)
- [file:line] Deprecated API pattern (if any)

### Optimization Suggestions
- [file:line] iOS 26+ pattern opportunity (if applicable)
```

Overall verdict rules:
- Any **FAIL** (theme, accessibility, ForEach identity, state management) → UI standards verdict = **FAIL**
- Any **WARNING**, no FAIL → UI standards verdict = **WARNING**
- All **PASS** → UI standards verdict = **PASS**
- **INFO** findings (iOS 26+) do not affect verdict (future-ready suggestions only)
- **Deprecated API WARNING** does not block (non-breaking modernization)
