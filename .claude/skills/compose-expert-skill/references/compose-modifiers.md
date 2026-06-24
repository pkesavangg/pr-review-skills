# Compose Modifiers & Layout — MeApp Android

## Modifier ordering matters

Order affects the visual result: modifiers apply **left to right**, each wrapping the next.

```kotlin
// ✅ Typical order: size/fill → clip → background → clickable → padding
Modifier
    .fillMaxWidth()
    .height(56.dp)
    .clip(RoundedCornerShape(MeTheme.borderRadius.sm))
    .background(MeTheme.colorScheme.primaryBackground)
    .clickable { onClick() }
    .padding(horizontal = MeTheme.spacing.md)

// ❌ Wrong: padding before clickable makes the padded area non-clickable
Modifier
    .padding(16.dp)
    .clickable { }  // only the inner, unpadded area responds to clicks
```

## Common layout patterns

```kotlin
// Full-width row with items spread apart
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) { ... }

// Box for overlapping / anchored elements
Box(modifier = Modifier.fillMaxWidth()) {
    Text(modifier = Modifier.align(Alignment.CenterStart), ...)
    Icon(modifier = Modifier.align(Alignment.CenterEnd), ...)
}

// Column with consistent spacing
Column(
    verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.md),
) { ... }
```

## Spacing — always use theme tokens

```kotlin
// ✅
.padding(horizontal = MeTheme.spacing.md, vertical = MeTheme.spacing.sm)
Arrangement.spacedBy(MeTheme.spacing.xs)

// ❌ Never hardcode dp values
.padding(horizontal = 16.dp)
```

## AppInputDefaults.SingleLineHeight

Use for any field that should match the standard input height (56dp):

```kotlin
Modifier.height(AppInputDefaults.SingleLineHeight)
```

## Reusable modifier builder pattern

```kotlin
// Define once, reuse across variants
private fun Modifier.cardStyle(): Modifier = this
    .fillMaxWidth()
    .clip(RoundedCornerShape(MeTheme.borderRadius.md))
    .background(MeTheme.colorScheme.primaryBackground)
    .padding(MeTheme.spacing.md)
```

## weight modifier in Row/Column

```kotlin
Row {
    Text("Label", modifier = Modifier.weight(1f))  // takes remaining space
    Icon(...)                                        // intrinsic size
}
```

## wrapContentWidth / wrapContentHeight

Use instead of `Modifier.width(IntrinsicSize.Max)` when the content should only take the space it needs:

```kotlin
Box(modifier = Modifier.wrapContentWidth()) { ... }
```
