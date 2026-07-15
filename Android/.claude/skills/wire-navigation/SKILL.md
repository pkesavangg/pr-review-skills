---
name: wire-navigation
description: Wire a new screen into the meApp Android navigation graph — the AppRoute sealed hierarchy and Navigation3. Use when the user says "navigate to X", "add a route for Y", "wire this screen", "push to Z", or after /feature-slice creates a new screen.
---

Wire a screen into the app's type-safe navigation.

The screen/route to wire is: $ARGUMENTS

## Instructions

### 1 — Read the current navigation
- `core/navigation/` — the `AppRoute` sealed classes and the Navigation3 host (backstack + `NavDisplay`/entry provider).
- An existing route entry most similar to yours — copy its shape.

> Before applying, pull the current Navigation3 pattern via **context7** (`androidx.navigation3`) or web fallback if the API is unfamiliar — Nav3 is evolving.

### 2 — Add the route
- Add a new `AppRoute` subtype (type-safe args as constructor params — not string paths).
- Register its entry in the navigation host so the backstack can render it.
- If it takes results back, use the project's result-passing pattern (check `core/navigation/` for the existing mechanism).

### 3 — Trigger navigation
- Navigate by pushing the `AppRoute` onto the backstack from the ViewModel/screen — don't build routes ad hoc in Composables.
- Handle back/up consistently with sibling screens.

### 4 — Verify
`cd Android && ./gradlew assembleDebug`, then `/verify-on-emulator` to confirm the screen pushes and pops correctly. Consider `/navigation-3` for advanced patterns (deep links, scenes, multiple backstacks).
