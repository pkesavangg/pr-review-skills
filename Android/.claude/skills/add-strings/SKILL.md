---
name: add-strings
description: Add UI string constants to the correct feature's strings/ PascalCase Strings object in the meApp Android app. Use when the user says "add a string for X", "add text/label for Y", when hardcoded text is found in a Composable, or when a new UI element needs a label.
---

Add static text to the right feature `strings/` object — never hardcode text in a Composable.

The string or label to add is: $ARGUMENTS

## Instructions

### 1 — Locate the feature's strings
- Look under `features/<feature>/strings/` for the PascalCase `Strings` object (e.g. `LoginStrings`).
- If the feature has none yet, create `strings/<Feature>Strings.kt` following the nearest existing one.

### 2 — Add the constant
- Add a descriptive PascalCase property (e.g. `LoginStrings.Title`, `LoginStrings.SubmitCta`).
- Group related strings; match the existing naming and ordering.
- Keep copy exactly as specified (mind approved wording, e.g. standardized validation copy).

### 3 — Use it
- Reference the constant from the Composable; remove the hardcoded literal.
- If the string needs localization resources, follow the feature's existing approach (resource id vs constant).

### 4 — Verify
`cd Android && ./gradlew assembleDebug`. Grep to confirm no hardcoded duplicate remains:
```bash
rg -n "\"<the text>\"" Android/app/src/main -g '*.kt'
```
