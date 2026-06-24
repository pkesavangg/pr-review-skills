---
name: add-accessibility
description: Systematically add accessibility labels, identifiers, Dynamic Type support, and VoiceOver semantics to a SwiftUI screen or component. Use when the user says "add accessibility to X", "make this screen accessible", "add VoiceOver support", "a11y fix", or when review-accessibility reports findings that need fixing.
---

Add accessibility support to a SwiftUI screen or component.

The target screen or component is: $ARGUMENTS

## Instructions

### 1 — Locate and Read the Target View

If `$ARGUMENTS` is a file path, read it directly.
If it's a screen/component name, search for it:

```bash
rg -l "$ARGUMENTS" meApp/Features -g '*.swift'
```

Read the file. Extract:
- **All interactive elements** — `Button`, `.onTapGesture`, `NavigationLink`, `Toggle`, `Slider`, `Picker`, `TextField`, `SecureField`
- **All images** — `Image(systemName:)`, `Image("")`, `AsyncImage`
- **All decorative elements** — dividers, background shapes, spacers with visual styling
- **All dynamic content** — conditional views based on state (loading, error, empty)
- **Feature name** — for `AccessibilityID` section naming

---

### 2 — Read the Existing Accessibility Pattern

Read the project's centralized identifiers:
```bash
cat meApp/Core/Utilities/AccessibilityIdentifiers.swift
```

Read one well-structured reference screen:
```bash
rg -l "accessibilityLabel" meApp/Features/Auth -g '*Screen.swift' | head -1
```

Read that file to calibrate the exact modifier style and placement used in this project.

---

### 3 — Add Accessibility Identifiers

Add stable identifiers to `AccessibilityIdentifiers.swift` for every interactive element in the target view. Follow the existing naming convention:

```swift
// MARK: - <FeatureName> Screen
static let <featureName><ElementDescription> = "<feature_name>_<element_description>"
```

Examples:
```swift
// MARK: - Dashboard Screen
static let dashboardWeightCard = "dashboard_weight_card"
static let dashboardPeriodPicker = "dashboard_period_picker"
static let dashboardGoalButton = "dashboard_goal_button"
```

Then add `.accessibilityIdentifier(AccessibilityID.<constant>)` to each element in the view file.

---

### 4 — Add Accessibility Labels

For each element type, apply the correct modifier:

**Icon-only buttons (no visible text):**
```swift
Button(action: { store.toggleSettings() }) {
    Image(systemName: "gear")
}
.accessibilityLabel("Settings")
.accessibilityIdentifier(AccessibilityID.dashboardSettingsButton)
```

**Buttons with visible text — no extra label needed**, but add identifier:
```swift
Button("Save") { store.save() }
    .accessibilityIdentifier(AccessibilityID.entrySaveButton)
```

**Decorative images — hide from VoiceOver:**
```swift
Image("background_pattern")
    .accessibilityHidden(true)
```

**Informational images — describe them:**
```swift
Image(systemName: "checkmark.circle.fill")
    .accessibilityLabel("Success")
```

**TextFields — add label if no visible associated label:**
```swift
TextField("", text: $email)
    .accessibilityLabel("Email address")
    .accessibilityIdentifier(AccessibilityID.loginEmailField)
```

**Custom interactive views (`.onTapGesture`):**
```swift
WeightCard(entry: entry)
    .onTapGesture { store.selectEntry(entry) }
    .accessibilityLabel("Weight entry for \(entry.formattedDate)")
    .accessibilityAddTraits(.isButton)
    .accessibilityIdentifier(AccessibilityID.dashboardWeightCard)
```

---

### 5 — Add Dynamic Type Support

Check if the view uses fixed font sizes or frames that would break with larger text:

**Replace fixed `.font(.system(size: N))` with semantic fonts:**
```swift
// Before:
.font(.system(size: 14))

// After:
.font(.subheadline)
```

**If custom Theme typography is used**, verify it scales. Check `Theme/Typography.swift` — if fonts are defined with fixed sizes, add `.dynamicTypeSize(...)` range to prevent layout breakage on extreme sizes:

```swift
.dynamicTypeSize(...DynamicTypeSize.accessibility2)
```

**Replace fixed-height frames on text containers:**
```swift
// Before:
Text(title).frame(height: 44)

// After:
Text(title).frame(minHeight: 44)
```

**Check ScrollView wrapping** — if content could overflow at large text sizes, ensure it's inside a `ScrollView`.

---

### 6 — Add Semantic Grouping

Group related elements so VoiceOver reads them as a single unit where appropriate:

```swift
HStack {
    Image(systemName: "scalemass")
    Text("\(weight) lbs")
}
.accessibilityElement(children: .combine)
.accessibilityLabel("Weight: \(weight) pounds")
```

For lists/grids, ensure each row is a single accessible element:
```swift
ForEach(entries) { entry in
    EntryRow(entry: entry)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(entry.formattedDate), \(entry.formattedWeight)")
}
```

---

### 7 — Handle State-Dependent Accessibility

If the view has loading, error, or empty states, each must be announced:

```swift
if store.isLoading {
    ProgressView()
        .accessibilityLabel("Loading")
} else if let error = store.errorMessage {
    Text(error)
        .accessibilityLabel("Error: \(error)")
} else if store.entries.isEmpty {
    Text("No entries yet")
        .accessibilityLabel("No weight entries recorded")
}
```

---

### 8 — Verify

After making changes, search for any remaining accessibility gaps:

```bash
# Count interactive elements vs accessibility modifiers in the file
rg -c "Button\|onTapGesture\|TextField\|SecureField\|Toggle\|Picker\|Slider\|NavigationLink" meApp/Features/<Feature>/Views/Screens/<ScreenName>.swift
rg -c "accessibilityLabel\|accessibilityIdentifier\|accessibilityHidden" meApp/Features/<Feature>/Views/Screens/<ScreenName>.swift
```

The accessibility modifier count should be >= the interactive element count.

---

### 9 — Report

```
Accessibility added to: {ScreenName}
File: {path}

AccessibilityID constants added: {count}
Accessibility labels added: {count}
Decorative elements hidden: {count}
Dynamic Type fixes: {count}
Semantic groups added: {count}

Interactive elements: {total} — all covered: ✅ / ❌
```

Follow up with:
- `/review-accessibility` to verify the changes pass
- `/gen-ui-test-file` if UI tests should target the new identifiers
