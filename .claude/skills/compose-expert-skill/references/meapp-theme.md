# MeApp Theme Tokens

## Access pattern

```kotlin
// In composables — always through MeAppTheme
MeAppTheme.colorScheme.primary
MeAppTheme.typography.body2
MeAppTheme.spacing.md
MeAppTheme.borderRadius.sm
```

Never use raw hex colors, hardcoded dp values for spacing, or direct Material3 `MaterialTheme` tokens where MeApp tokens exist.

## Common color tokens

| Token | Use case |
|---|---|
| `colorScheme.primary` | Primary accent / CTA |
| `colorScheme.primaryBackground` | Input field / card background (white) |
| `colorScheme.secondaryBackground` | Screen background (warm off-white) |
| `colorScheme.textBody` | Main body text (#2c2827) |
| `colorScheme.textSubheading` | Placeholder / secondary text (#7b726e) |
| `colorScheme.textHeading` | Headings / bold values (#2c2827) |
| `colorScheme.utility` | Disabled state |

## Typography tokens

| Token | Style |
|---|---|
| `typography.heading3` | Large headings |
| `typography.heading4` | Section headings |
| `typography.heading5` | Bold 16px (value text in pickers) |
| `typography.heading6` | Note titles |
| `typography.body1` | Message text |
| `typography.body2` | Standard body (Regular 16px) |
| `typography.body3` | Trailing text in inputs |
| `typography.subHeading1` | Placeholder / secondary 16px |
| `typography.subHeading2` | Smaller secondary text |

## TextType → Typography mapping

| TextType | Typography | Weight |
|---|---|---|
| `Title` | heading4 | Bold |
| `Subtitle` | subHeading1 | Regular |
| `Body` | body2 | Regular |
| `SubHeading` | subHeading1 | Regular |
| `ListTitle1` | heading5 | Bold |
| `ListTitle2` | heading4 | Bold |

## Spacing tokens (approximate)

| Token | Value |
|---|---|
| `spacing.xs` | 4dp |
| `spacing.sm` | 8dp |
| `spacing.md` | 16dp |
| `spacing.lg` | 24dp |
| `spacing.xl` | 32dp |

## Previews

```kotlin
@PreviewTheme      // multi-preview: light + dark + large font
@Composable
fun FooPreview() {
    MeAppTheme {
        FooScreen(state = FooState(), onIntent = {})
    }
}
```

## Common shared composables (prefer over raw Material3)

- `AppText` — use `TextType` for consistent typography
- `AppInput` — text fields with `trailingText`, `imeAction`, `onImeAction`, `FocusRequester`
- `AppStyledCard` — card container matching Figma
- `AppButton` — primary / secondary CTA
- `SegmentButtonGroup` — pill-style toggle (Ft/In vs CM, etc.)
- `AppHeightPickerModal` — height selection via wheel picker
- `ModalDialog` — standard modal wrapper
