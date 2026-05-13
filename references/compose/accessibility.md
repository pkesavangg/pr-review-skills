# Compose — Accessibility

Rules covering TalkBack, Switch Access, font scaling, and Material accessibility guarantees. Sourced from the official [Compose accessibility docs](https://developer.android.com/jetpack/compose/accessibility) and Material's [accessibility guidelines](https://m3.material.io/foundations/accessible-design/overview).

---

## P0 — Control unreachable to assistive tech

**Clickable composable with no semantic label.** TalkBack reads nothing; the user can't tell what the control does.

```kotlin
// BAD — TalkBack announces "Button" with no label
Box(modifier = Modifier.clickable(onClick = onSubmit)) {
    Icon(Icons.Default.Send, contentDescription = null)
}
```

Fix: either use a `Button` / `IconButton` with a label/`contentDescription`, or attach semantics:

```kotlin
// GOOD
IconButton(onClick = onSubmit) {
    Icon(Icons.Default.Send, contentDescription = "Send message")
}

// or, custom clickable
Box(
    modifier = Modifier
        .clickable(onClickLabel = "Send message", onClick = onSubmit)
        .semantics { role = Role.Button }
) { … }
```

---

## P1 — Missing or wrong `contentDescription`

**`Icon` / `Image` with `contentDescription = null` on a clickable parent**, when the icon IS the content.

`null` is correct only when the element is **purely decorative** and an adjacent text label describes it. If it's clickable and *the icon is the affordance*, you need a description.

```kotlin
// BAD — icon-only button with no description
IconButton(onClick = onClose) {
    Icon(Icons.Default.Close, contentDescription = null)
}

// GOOD
IconButton(onClick = onClose) {
    Icon(Icons.Default.Close, contentDescription = "Close")
}

// GOOD — decorative icon next to a labeled Button
Button(onClick = onSave) {
    Icon(Icons.Default.Save, contentDescription = null)   // decorative
    Spacer(Modifier.size(8.dp))
    Text("Save changes")                                  // does the labeling
}
```

If `contentDescription = null` is intentional, prefer adding a comment so reviewers can verify.

---

## P1 — Hit target too small

Material guidelines require **≥ 48dp × 48dp** for interactive targets. Material components enforce this by default — but custom composables don't.

```kotlin
// BAD — 24dp icon button is too small to tap reliably
Box(
    modifier = Modifier
        .size(24.dp)
        .clickable { … }
) { Icon(Icons.Default.Close, contentDescription = "Close") }

// GOOD — content stays 24dp, hit area is 48dp
IconButton(onClick = { … }, modifier = Modifier.size(48.dp)) {
    Icon(Icons.Default.Close, contentDescription = "Close")
}
```

For custom hit areas, use `Modifier.minimumInteractiveComponentSize()` (Material 3) or set `size(48.dp)` explicitly.

---

## P1 — Manual click handling without role

`Modifier.clickable { … }` on a `Box` or `Row` makes it focusable for TalkBack, but TalkBack reads "double-tap to activate" generically. Assistive tech needs a `Role` to announce the kind of control.

```kotlin
// BAD — TalkBack says "Item, double-tap to activate"
Row(modifier = Modifier.clickable(onClick = onSelect)) { … }

// GOOD — TalkBack says "Item, button"
Row(
    modifier = Modifier
        .clickable(onClick = onSelect)
        .semantics { role = Role.Button }
) { … }
```

Roles available: `Role.Button`, `Role.Checkbox`, `Role.Switch`, `Role.RadioButton`, `Role.Tab`, `Role.Image`, `Role.DropdownList`.

---

## P1 — Font scaling broken

**Hardcoded `sp` for text or `dp` where text size should drive layout.** When the user increases system font scale, text overflows or truncates.

```kotlin
// BAD — text size doesn't scale; container can't grow
Box(modifier = Modifier.height(24.dp)) {
    Text("Hello", fontSize = 14.sp)
}

// GOOD — use MaterialTheme typography (already scaled by system)
Text("Hello", style = MaterialTheme.typography.bodyMedium)
```

Don't disable font scaling unless there's a documented reason (rare; ask for justification).

---

## P1 — Grouping interactive content

When a `Row` / `Column` contains multiple interactive items that act as one (e.g., a checkbox plus its label), TalkBack should announce them as a single control.

```kotlin
// BAD — TalkBack focuses checkbox and label separately
Row {
    Checkbox(checked, onCheckedChange)
    Text(label)
}

// GOOD — single semantic group
Row(
    modifier = Modifier
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
) {
    Checkbox(checked, onCheckedChange = null)
    Text(label)
}
```

---

## P2 — Missing live region

Status messages that update without user interaction (error banners, loading states) should be announced. Use `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`.

---

## P2 — Reduce Motion not respected

Animations should honor the user's "Remove animations" setting. Read `LocalAccessibilityManager` or check `Animation.areAnimationsEnabled()` and fall back to instant transitions when disabled. Material components in `androidx.compose.material3` 1.2+ honor this automatically; custom animations need to check.

---

## P2 — Heading semantics on titles

Section/screen titles should be marked as headings so TalkBack users can navigate by heading.

```kotlin
Text(
    text = "Settings",
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.semantics { heading() }
)
```

---

## Nit

- `Image` of a user avatar with `contentDescription = "User profile picture"` — prefer "Profile picture of <name>" when name is known.
- `IconButton` is preferred over `Box(Modifier.clickable)` wrapping an icon — it handles the 48dp hit area and ripple automatically.
