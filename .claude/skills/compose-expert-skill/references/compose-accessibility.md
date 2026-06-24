# Compose Accessibility — MeApp Android

## Requirements

- Every **interactive** element (Icon, Image used as button, custom clickable) must have a `contentDescription`.
- Hit targets must be ≥ 48dp.
- Dynamic type (text scaling) must not clip text — avoid fixed heights on text-containing elements.

## contentDescription

```kotlin
// Icon with action — always describe the action
Icon(
    painter = painterResource(AppIcons.Default.ChevronDown),
    contentDescription = null,  // ✅ decorative-only (inside a clickable Box that has its own description)
)

// Icon that IS the clickable — describe it
IconButton(onClick = { ... }) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "Delete entry",  // ✅
    )
}
```

## semantics

```kotlin
// Merge descendants for a card/list item
Modifier.semantics(mergeDescendants = true) { }

// Custom action
Modifier.semantics {
    contentDescription = "Height picker: 6 feet 8 inches. Tap to change."
    onClick(label = "Open height picker") { true }
}
```

## Hit targets

```kotlin
// Ensure 48dp minimum touch area even for small icons
Modifier.size(24.dp).padding(12.dp)  // 48dp total touch target
// Or use Modifier.minimumInteractiveComponentSize()  (Material3)
```

## TalkBack testing checklist

- [ ] Every interactive element announces its purpose when focused
- [ ] Reading order follows visual left-to-right, top-to-bottom
- [ ] State changes are announced (loading, error, success)
- [ ] Custom picker/modal is reachable via TalkBack swipe navigation

## Error messages

Error text must be programmatically associated with its field:

```kotlin
// AppInput handles this via supportingText
AppInput(
    formControl = heightControl,
    supportingText = heightControl.error,  // shows in red below field
)
```
