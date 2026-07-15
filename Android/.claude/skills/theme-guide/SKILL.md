---
name: theme-guide
description: Apply the meApp Android design system — MeAppTheme tokens for colors, typography, and spacing — in Compose. Use when working with design tokens, adding a color, styling a Composable, matching a Figma spec, or when a review flags hardcoded colors/sizes.
---

Apply `MeAppTheme` tokens. Never hardcode colors, typography, or spacing.

> Before applying, pull the current Compose **Material 3** pattern via **context7** (`androidx.compose.material3`) or web fallback — component APIs and theming conventions evolve.

The theming task is: $ARGUMENTS

## Instructions

### 1 — Read the theme
- Find the `MeAppTheme` definition and its token groups: `.colorScheme`, `.typography`, `.spacing` (and radius if present).
- Read a well-styled existing Composable for the access pattern.

### 2 — Use tokens, not literals
- Colors → `MeAppTheme.colorScheme.<role>` (never `Color(0x…)` or raw hex).
- Type → `MeAppTheme.typography.<style>` (never inline `TextStyle`/`fontSize`).
- Spacing/padding → `MeAppTheme.spacing.<step>` (never magic `dp`).
- Prefer shared components in `features/common/` before dropping to Material3 defaults.

### 3 — Adding a new token
- Add it to the theme's token source (not at the call site) so it's reusable and theme-aware (light/dark).
- If it comes from Figma, map the Figma variable → the token name; keep names semantic (role-based), not literal.

### 4 — Previews
- Every new Composable gets `@PreviewTheme` previews wrapped in `MeAppTheme { ... }` so both light/dark render.

### 5 — Verify
`cd Android && ./gradlew assembleDebug`; check previews render; `/verify-on-emulator` for a real-device look.
