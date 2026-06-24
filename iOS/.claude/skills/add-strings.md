---
name: add-strings
description: Add new string constants to the feature's Strings/ PascalCase struct. Use when the user says "add string for X", "add text for Y", "add localization for Z", when hardcoded text is found in a SwiftUI view, or when a new UI element needs a label.
---

Add localized string constants to the correct feature Strings/ struct.

The string or label to add is: $ARGUMENTS

## Instructions

### 1 — Locate the Feature Strings File

Determine which feature the string belongs to, then find its Strings file:

```bash
rg -l "struct.*Strings" meApp/Features -g '*.swift'
```

If the feature has no Strings file yet, create one at:
`meApp/Features/<FeatureName>/Strings/<FeatureName>Strings.swift`

```swift
import Foundation

struct <FeatureName>Strings {
}
```

---

### 2 — Check for Duplicates

Before adding, search for the same or equivalent value:

```bash
rg -n "<string text or keyword>" meApp/Features -g '*.swift'
```

If an equivalent string already exists in this feature's Strings file, use it rather than creating a duplicate.

---

### 3 — Add the Constant

Follow project naming conventions:
- Struct name: `<FeatureName>Strings` (PascalCase)
- Constant name: descriptive PascalCase noun (`Title`, `SaveButton`, `ErrorMessage`, `EmptyStateMessage`)
- If the file uses nested structs (e.g. `struct Errors { ... }`), match that pattern

```swift
struct <FeatureName>Strings {
    static let <ConstantName> = "<string value>"
}
```

---

### 4 — Update the Call Site

If a view currently has a hardcoded string literal, replace it:

```swift
// Before:
Text("Save changes")

// After:
Text(<FeatureName>Strings.SaveButton)
```

Search for other occurrences of the hardcoded value in case it appears multiple times:

```bash
rg -n "<hardcoded text>" meApp/Features -g '*.swift'
```

---

### 5 — Report

```
String added: <FeatureName>Strings.<ConstantName> = "<value>"
File: meApp/Features/<Feature>/Strings/<FeatureName>Strings.swift
Used in: <file path(s) where the string is referenced>
```
