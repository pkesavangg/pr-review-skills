---
name: theme-guide
description: Theming system reference — colors, typography, spacing, and border radius. Use when working with design tokens, adding new colors, updating themes, or applying theme values to views. Part of the design system sync workflow from Figma.
---

# meApp iOS Theme System Guide

The theming system implements a **three-layer color architecture** with semantic roles, a **8-point spacing grid**, and **Figma-driven typography and radius tokens**. All design tokens flow from Figma → Assets catalog → Token enums → SwiftUI views.

---

## Quick Reference: Theme Architecture

```
Figma Design System (source of truth)
        ↓
iOS Assets.xcassets (color swatches for light/dark mode)
        ↓
Theme/Tokens/ enums (spacing, border radius, etc.)
        ↓
Theme/ color system (ColorTokens, AppColors.Palette)
        ↓
@Environment(\.appTheme) — injected into views
        ↓
View rendering with theme.colorRole (e.g., theme.textBody)
```

---

## Colors: 3-Layer System

### Layer 1 — Raw Tokens (ColorTokens)

Raw `Color` values sourced from the asset catalog. In `Theme/Tokens/ColorTokens.swift`:

```swift
struct ColorTokens {
    // Neutral palette
    static let neutral50 = Color("neutral-50")
    static let neutral100 = Color("neutral-100")
    static let neutral200 = Color("neutral-200")
    // ... continues through neutral900
    
    // Brand colors
    static let primary500 = Color("primary-500")
    static let primary600 = Color("primary-600")
    // ... accent, success, warning, error palettes
}
```

**Where they live:** `Assets.xcassets/` contains `.colorset` folders:
- `BaseColors/` — neutral grays, semantic roles (light mode)
- Dark mode support: Each colorset has `.dark` appearance variant

### Layer 2 — Semantic Palette (AppColors.Palette)

Maps raw tokens → semantic roles. In `Theme/Color.swift`, the `Palette` struct:

```swift
struct Palette {
    // Text colors
    let textHeading: Color       // For h1-h5 text
    let textBody: Color          // For body copy (p1-p3)
    let textMuted: Color         // For secondary/disabled text
    let textInverse: Color       // For white-on-dark text
    
    // Background colors
    let backgroundPrimary: Color       // Main content background
    let backgroundSecondary: Color     // Card/section backgrounds
    let backgroundTertiary: Color      // Subtle backgrounds
    
    // Action colors
    let actionPrimary: Color      // Primary buttons, links
    let actionSecondary: Color    // Secondary buttons
    let actionTertiary: Color     // Tertiary buttons
    let actionDisabled: Color     // Disabled state
    
    // Status colors
    let statusSuccess: Color      // For ✓ checkmarks, positive states
    let statusError: Color        // For ✗ errors, destructive actions
    let statusWarning: Color      // For ⚠ warnings
    let statusInfo: Color         // For ℹ info messages
    
    // Support colors
    let supportToastBackground: Color    // Toast/notification BG
    let borderDefault: Color              // Default border color
    let divider: Color                    // Line dividers
}
```

**How it's constructed:** In `ColorTokens.Palette`:

```swift
static var primary: Palette {
    return Palette(
        textHeading: ColorTokens.neutral900,
        textBody: ColorTokens.neutral800,
        textMuted: ColorTokens.neutral600,
        // ... continues for all 20+ roles
    )
}
```

### Layer 3 — Environment Injection

The active palette is injected via `@Environment(\.appTheme)` using `AppThemeKey`:

```swift
struct AppThemeKey: EnvironmentKey {
    static let defaultValue: AppColors.Palette = ColorTokens.Palette.primary
}

extension EnvironmentValues {
    var appTheme: AppColors.Palette {
        get { self[AppThemeKey.self] }
        set { self[AppThemeKey.self] = newValue }
    }
}
```

Applied once at app root via `ThemeableModifier`:

```swift
var body: some View {
    TabNavigationView()
        .themeable()  // Propagates theme down to all descendants
}
```

---

## Using Colors in Views

**Always inject the theme environment variable:**

```swift
struct MyView: View {
    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: .spacingMD) {
            Text("Heading")
                .foregroundColor(theme.textHeading)
            
            Text("Body text")
                .foregroundColor(theme.textBody)
            
            Button("Action") { }
                .foregroundColor(theme.actionPrimary)
                .background(theme.backgroundPrimary)
        }
    }
}
```

**❌ NEVER:**

```swift
Text("Bad").foregroundColor(Color.blue)                    // Hardcoded color
Text("Bad").foregroundColor(Color("neutral-800"))         // Direct asset access
Text("Bad").foregroundColor(ColorTokens.neutral800)       // Raw token use
```

---

## Typography

Typography is driven by **Open Sans font** with a **8-step scale** on mobile, matching Figma spec.

### Font Sizes and Weights (from Figma)

| Style | Mobile | Tablet | Font | Example |
|-------|--------|--------|------|---------|
| **Heading 1** | 60px, Extra Bold | 70px, Extra Bold | Open Sans | Major section titles |
| **Heading 2** | 50px, Extra Bold | 50px, Extra Bold | Open Sans | Section headers |
| **Heading 3** | 36px, Bold | 38px, Bold | Open Sans | Card titles |
| **Heading 4** | 24px, Bold | 28px, Bold | Open Sans | Subsection heads |
| **Heading 5** | 16px, Bold | 18px, Bold | Open Sans | Small headers |
| **Heading 6** | 14px, Bold | 18px, Bold | Open Sans | Labels |
| **Body 1** | 20px, Regular | 22px, Regular | Open Sans | Large body text |
| **Body 2** | 16px, Regular | 18px, Regular | Open Sans | Standard body text |
| **Body 3** | 14px, Regular | 16px, Regular | Open Sans | Secondary body |
| **Body 4** | 12px, Regular | 14px, Regular | Open Sans | Small text |
| **Body 5** | 10px, Regular | 12px, Regular | Open Sans | Tiny text |
| **Link 1** | 16px, Bold | 18px, Bold | Open Sans | Large links |
| **Link 2** | 12px, Bold | 14px, Bold | Open Sans | Small links |
| **Button 1** | 16px, Bold | 18px, Bold | Open Sans | Large buttons |
| **Button 2** | 14px, Bold | 16px, Bold | Open Sans | Standard buttons |
| **Button 3** | 20px, Bold | 20px, Bold | Open Sans | Extra large buttons |

### Usage Pattern

In `Theme/Typography.swift`, use font extension methods:

```swift
Text("Heading Text")
    .fontOpenSans(.heading1)

Text("Body copy with custom weight")
    .fontOpenSans(.body2)
    .fontWeight(.bold)
```

Or use the `Typography` struct helper:

```swift
let font = Font(Typography.heading3)
Text("Title").font(font)
```

**❌ Never use `Font.system(size:weight:)`** — always use the theme typography.

---

## Spacing: 8-Point Grid

Spacing follows a **strict 8-point grid** (4px, 8px, 16px, 24px, 32px, 40px, 48px, 56px, 64px, 72px, 80px).

### Spacing Tokens (from Figma)

| Token | Pixels | Use Case |
|-------|--------|----------|
| `xxs` | 4px | Micro spacing (icon+text gap) |
| `xs` | 8px | Small spacing (form field gaps) |
| `sm` | 16px | Standard padding (cards, buttons) |
| `md` | 24px | Section spacing |
| `lg` | 32px | Major spacing (between sections) |
| `xl` | 40px | Large spacing (screen padding) |
| `2xl` | 48px | Extra large spacing |
| `3xl` | 56px | Very large spacing |
| `4xl` | 64px | Huge spacing |
| `5xl` | 72px | Massive spacing |
| `6xl` | 80px | Maximum spacing |

### Usage via `CGFloat` Extension

In `Theme/Tokens/Spacing.swift`, tokens are exposed as computed properties:

```swift
VStack(spacing: .spacingMD) {          // 24px spacing
    Text("Section 1")
    Text("Section 2")
}
.padding(.horizontal, .spacingSM)      // 16px horizontal padding
.padding(.vertical, .spacingLG)        // 32px vertical padding

Divider()
    .padding(.vertical, .spacingXS)    // 8px above/below
```

**Shorthand mapping:**

```swift
.spacingXXS  = 4px
.spacingXS   = 8px
.spacingSM   = 16px
.spacingMD   = 24px
.spacingLG   = 32px
.spacingXL   = 40px
.spacing2XL  = 48px
.spacing3XL  = 56px
.spacing4XL  = 64px
.spacing5XL  = 72px
.spacing6XL  = 80px
```

**❌ Never hardcode `padding(12)` or `spacing(20)` —** always use grid tokens.

---

## Border Radius: Rounded Corners

Border radius follows a **6-level scale** from subtle (4px) to fully rounded (999px).

### Border Radius Tokens (from Figma)

| Token | Pixels | Use Case |
|-------|--------|----------|
| `xs` | 4px | Subtle rounding (small elements) |
| `sm` | 8px | Light rounding (icons, small buttons) |
| `md` | 12px | Standard rounding (cards) |
| `lg` | 16px | Medium rounding (larger cards, modals) |
| `xl` | 28px | Large rounding (prominent containers) |
| `2xl` | 44px | Extra large rounding |
| `full` / `pill` | 999px | Fully rounded (circular, pills) |

### Usage

In `Theme/Tokens/BorderRadius.swift`:

```swift
RoundedRectangle(cornerRadius: .radiusSM)
    .frame(width: 100, height: 100)

Card {
    Text("Content")
}
.cornerRadius(.radiusMD)

Button("Pill Button") { }
    .cornerRadius(.radiusPill)
```

**Shorthand mapping:**

```swift
.radiusXS   = 4px
.radiusSM   = 8px
.radiusMD   = 12px
.radiusLG   = 16px
.radiusXL   = 28px
.radius2XL  = 44px
.radiusPill = 999px
```

---

## Dark Mode Support

The theming system automatically supports **light and dark appearances** via iOS's `colorSet` feature.

### How It Works

Each color in `Assets.xcassets/` has two variants:

```
Assets.xcassets/
├── BaseColors/
│   ├── neutral-50.colorset/
│   │   ├── Colors.json (light mode)
│   │   └── Colors.json (dark appearance)
│   ├── primary-500.colorset/
│   │   ├── ...
```

When you define `Color("neutral-50")`, iOS automatically selects the light or dark variant based on the current interface style (`@Environment(\.colorScheme)`).

### No Code Changes Needed

The palette system (`AppColors.Palette`) automatically works for both modes — no conditional logic required.

```swift
// This automatically uses the correct color for light/dark mode:
Text("Hello").foregroundColor(theme.textHeading)
```

---

## Adding a New Color to the Design System

### Workflow: Figma → Assets → Tokens → Views

**Step 1: Define in Figma Design System**

- Add the color to Figma's Colors board (node ID `6231:64130`)
- Document: name, light mode hex, dark mode hex
- Example: `primary-700` #0047AB (light), #0055D4 (dark)

**Step 2: Add Color Swatch to Assets**

1. Open `meApp.xcodeproj` → `Assets.xcassets`
2. Right-click → **New Color Set**
3. Name it to match Figma: `primary-700`
4. In Attributes Inspector:
   - Set **Appearances** to "Light, Dark"
   - Light mode: paste hex from Figma
   - Dark mode: paste dark variant hex
5. Save and commit

**Step 3: Add to ColorTokens**

In `Theme/Tokens/ColorTokens.swift`:

```swift
struct ColorTokens {
    // ... existing colors ...
    
    // Add new primary shade
    static let primary700 = Color("primary-700")  // NEW
    
    // Add to palette construction if it's a semantic role
    static var primary: Palette {
        Palette(
            actionPrimary: ColorTokens.primary600,  // or primary700 if changing
            // ... other roles ...
        )
    }
}
```

**Step 4: Add Semantic Role (if new role)**

If this is a new semantic role (e.g., a new status color):

In `Theme/Color.swift`, add to `Palette` struct:

```swift
struct Palette {
    // ... existing roles ...
    let statusSuccess: Color      // NEW
    let statusError: Color
}
```

Then update `ColorTokens.Palette.primary`:

```swift
static var primary: Palette {
    Palette(
        // ... existing properties ...
        statusSuccess: ColorTokens.successGreen,
        statusError: ColorTokens.errorRed,
    )
}
```

**Step 5: Use in Views**

```swift
Text("Success!")
    .foregroundColor(theme.statusSuccess)  // Automatic light/dark mode
```

---

## Common Theming Patterns

### Pattern 1: Color + Background

```swift
VStack {
    Text("Card Title")
        .foregroundColor(theme.textHeading)
}
.background(theme.backgroundSecondary)
.cornerRadius(.radiusMD)
.padding(.spacingSM)
```

### Pattern 2: Dark Mode Lists

```swift
List {
    ForEach(items) { item in
        Text(item.name)
            .foregroundColor(theme.textBody)
    }
}
.listRowBackground(theme.backgroundPrimary)
.scrollContentBackground(.hidden)
.background(theme.backgroundSecondary)
```

### Pattern 3: Button Styling

```swift
Button(action: {}) {
    Text("Submit")
        .fontOpenSans(.button2)
        .foregroundColor(.white)
        .frame(maxWidth: .infinity)
        .padding(.spacingSM)
        .background(theme.actionPrimary)
        .cornerRadius(.radiusSM)
}
```

### Pattern 4: Disabled State

```swift
Button("Action") { }
    .foregroundColor(isDisabled ? theme.actionDisabled : theme.actionPrimary)
    .disabled(isDisabled)
```

---

## Golden Rules

1. **Environment injection first** — Always `@Environment(\.appTheme) private var theme` at view top
2. **No hardcoded colors** — Never use `Color.blue`, `Color(hex:)`, or `Color("name")` directly in views
3. **Use semantic roles** — `theme.textHeading`, not `theme.neutral900` (roles adjust for dark mode)
4. **Grid-based spacing** — Only use `.spacingXS`, `.spacingMD`, etc.; never hardcode pixels
5. **Rounded corners via tokens** — Use `.radiusSM`, `.radiusMD`, not raw corner radius values
6. **Typography from theme** — Use `.fontOpenSans(.heading3)`, not `Font.system(size:weight:)`
7. **Dark mode is automatic** — No `@Environment(\.colorScheme)` checks needed; assets handle it
8. **Multi-account themes** — `Theme.shared` is per-account; switching accounts updates all descendants

---

## File Reference

| File | Purpose |
|------|---------|
| `meApp/Theme/Color.swift` | `AppColors` enum + `Palette` struct + `ThemeableModifier` |
| `meApp/Theme/Typography.swift` | Font extensions + `Typography` helper |
| `meApp/Theme/Tokens/ColorTokens.swift` | Raw color tokens + palette construction |
| `meApp/Theme/Tokens/Spacing.swift` | `CGFloat` spacing extensions (.spacingMD, etc.) |
| `meApp/Theme/Tokens/BorderRadius.swift` | `CGFloat` radius extensions (.radiusSM, etc.) |
| `meApp/Theme/Enums/AppColorScheme.swift` | Color scheme enum |
| `meApp/Theme/Enums/AppearanceMode.swift` | Theme appearance (light/dark/system) |
| `meApp/Theme/Enums/CustomTextStyle.swift` | Custom text style enum for OpenSans font |
| `Assets.xcassets/BaseColors/` | Color swatches (light/dark variants) |

---

## Related Skills

- **`/feature-slice`** — Scaffolds views with proper theme injection
- **`/add-preview`** — Preview scaffold wraps in `.themeable()` + mock palette
- **`/review-ui-standards`** — Audits for hardcoded colors, missing theme references
- **`/config-change`** — Theme configuration and appearance mode switching

---

## Figma Design System Links

- **Colors:** [Figma Colors Board](https://www.figma.com/design/W7fZI4HJ9QC95NamHi92BG/Me.Health-Mega-App?node-id=6231-64130&t=WjEQKdEYq3mrJ7dR-4)
- **Spacing:** [Figma Spacing Tokens](https://www.figma.com/design/W7fZI4HJ9QC95NamHi92BG/Me.Health-Mega-App?node-id=5652-38511&t=WjEQKdEYq3mrJ7dR-4)
- **Border Radius:** [Figma Radius Tokens](https://www.figma.com/design/W7fZI4HJ9QC95NamHi92BG/Me.Health-Mega-App?node-id=6102-100248&t=WjEQKdEYq3mrJ7dR-4)
- **Typography:** [Figma Typography Scale](https://www.figma.com/design/W7fZI4HJ9QC95NamHi92BG/Me.Health-Mega-App?node-id=11166-303812&t=WjEQKdEYq3mrJ7dR-4)
- **Effects:** [Figma Effects/Shadows](https://www.figma.com/design/W7fZI4HJ9QC95NamHi92BG/Me.Health-Mega-App?node-id=6099-99915&t=WjEQKdEYq3mrJ7dR-4)
- **Stroke Width:** [Figma Stroke Tokens](https://www.figma.com/design/W7fZI4HJ9QC95NamHi92BG/Me.Health-Mega-App?node-id=10448-294400&t=WjEQKdEYq3mrJ7dR-4)
