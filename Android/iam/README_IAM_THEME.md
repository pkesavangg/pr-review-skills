# IAM Theme System

The IAM (In-App Messaging) package now includes a comprehensive theme system that allows it to receive colors from the main app while maintaining independence. This system provides seamless integration with the main app's theme while ensuring IAM components work correctly in all scenarios.

## Overview

The IAM theme system consists of several key components:

1. **IAMColorScheme** - The main color scheme that provides semantic color tokens
2. **IAMTheme** - A composable theme wrapper that can receive colors from the main app
3. **LocalComposition** - A mechanism for the main app to provide its colors to IAM
4. **Preview Themes** - Pre-configured themes for Compose previews
5. **Color Mapping** - Utilities for converting between app and IAM color schemes

## Key Features

- **Independence**: IAM components work without the main app's theme
- **Integration**: Can receive and use colors from the main app when available
- **Fallbacks**: Sensible defaults ensure components always have colors
- **Preview Support**: Dedicated preview themes for development
- **Consistency**: Follows the same patterns as the main app's theme system

## Usage

### Basic Usage in IAM Components

```kotlin
@Composable
fun MyIAMComponent() {
    // Method 1: Using IAMTheme object (recommended)
    val colors = IAMTheme.colorScheme
    
    // Method 2: Using rememberIAMColorScheme
    val colors = rememberIAMColorScheme(isDarkMode = false)
    
    // Method 3: Using rememberIAMColors (from configuration)
    val colors = rememberIAMColors()
    
    Column(
        modifier = Modifier
            .background(colors.backgroundPrimary)
            .padding(16.dp)
    ) {
        Text(
            text = "Hello IAM",
            color = colors.textHeading
        )
        
        Button(
            onClick = { /* Handle click */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.actionPrimary
            )
        ) {
            Text(
                text = "Click Me",
                color = colors.actionInverse
            )
        }
    }
}
```

### Using IAM Theme Wrapper

```kotlin
@Composable
fun MyIAMScreen() {
    IAMTheme {
        // All child components can access IAM colors via IAMTheme.colorScheme
        MyIAMComponent()
    }
}
```

### Providing Colors from Main App

The main app can provide its colors to IAM components by wrapping them in `IAMTheme`:

```kotlin
@Composable
fun AppScreen() {
    MeAppTheme {
        Column {
            // App content
            AppContent()
            
            // IAM content with app colors
            IAMTheme {
                IAMComponent()
            }
        }
    }
}
```

## Color System

### Semantic Color Roles

The IAM color system provides semantic color roles that map to design tokens:

- **Background**: `backgroundPrimary`, `backgroundSecondary`, `backgroundCard`
- **Text**: `textHeading`, `textBody`, `textSubheading`, `textError`
- **Actions**: `actionPrimary`, `actionSecondary`, `actionTertiary`
- **Status**: `statusSuccess`, `statusWarning`, `statusDanger`
- **Icons**: `iconPrimary`, `iconSecondary`, `iconSuccess`
- **Support**: `overlay`, `toastBackground`, `divider`

### Color Priority

Colors are resolved in the following priority order:

1. **App Colors** - When provided by the main app via `IAMTheme`
2. **IAM Defaults** - Fallback to IAM-specific color tokens
3. **System Defaults** - Final fallback for edge cases

### Theme-Specific Colors

IAM provides theme-specific colors for promotional content:

```kotlin
val colors = IAMTheme.colorScheme

// Get theme colors
val redTheme = colors.themeRed
val blueTheme = colors.themeBlue
val greenTheme = colors.themeGreen

// Get promo code colors
val promoColors = colors.getPromoCodeColors("blue")
val background = promoColors.background
val text = promoColors.text
val copyButton = promoColors.copyButton
```

## Preview Themes

IAM provides several preview themes for development:

```kotlin
@Preview(showBackground = true)
@Composable
fun MyComponentPreview() {
    IAMPreviewTheme {
        MyIAMComponent()
    }
}

@Preview(showBackground = true)
@Composable
fun MyComponentPreviewDark() {
    IAMPreviewThemeDark {
        MyIAMComponent()
    }
}

@Preview(showBackground = true)
@Composable
fun MyComponentPreviewParameterized() {
    IAMPreviewThemeParameterized(isDarkMode = false) {
        MyIAMComponent()
    }
}
```

## Integration with Main App

### Automatic Integration

When IAM components are used within `MeAppTheme`, they automatically receive the app's colors:

```kotlin
@Composable
fun AppWithIAM() {
    MeAppTheme {
        // IAM components automatically get app colors
        IAMComponent()
    }
}
```

### Manual Integration

For more control, the main app can manually provide colors:

```kotlin
@Composable
fun AppWithManualIAM() {
    val appColors = MeTheme.colorScheme
    val iamColors = IAMColorScheme.fromAppColors(
        appColorMapping = IAMAppColorMapper.mapAppColorScheme(appColors),
        isDarkMode = isSystemInDarkTheme()
    )
    
    IAMTheme(iamColorScheme = iamColors) {
        IAMComponent()
    }
}
```

## Configuration

### IAM Configuration

IAM components can be configured globally:

```kotlin
// Update IAM colors with app colors
IAMConfiguration.updateIAMColorScheme(iamColorScheme)

// Check if configuration is initialized
val isInitialized = IAMConfiguration.isInitialized()

// Get current configuration
val currentColors = IAMConfiguration.getIAMColorScheme()
```

### Color Mapping

The system automatically maps between app and IAM color schemes:

```kotlin
// Map app ColorScheme to IAM AppColorMapping
val appColorMapping = IAMAppColorMapper.mapAppColorScheme(appColorScheme)

// Create IAMColorScheme from app colors
val iamColorScheme = IAMColorScheme.fromAppColors(appColorMapping, isDarkMode)
```

## Best Practices

### 1. Use IAMTheme.colorScheme

Prefer `IAMTheme.colorScheme` over other methods for consistent access to colors:

```kotlin
// ✅ Good
val colors = IAMTheme.colorScheme

// ❌ Avoid
val colors = rememberIAMColors()
```

### 2. Wrap IAM Content in IAMTheme

Always wrap IAM content in `IAMTheme` to ensure proper color resolution:

```kotlin
// ✅ Good
IAMTheme {
    IAMComponent()
}

// ❌ Avoid
IAMComponent() // May not have proper colors
```

### 3. Use Preview Themes

Use IAM preview themes for development and testing:

```kotlin
@Preview(showBackground = true)
@Composable
fun Preview() {
    IAMPreviewTheme {
        MyComponent()
    }
}
```

### 4. Handle Missing Colors Gracefully

IAM components should work even when app colors are not available:

```kotlin
val colors = IAMTheme.colorScheme
// Colors will always be available, either from app or IAM defaults
```

## Migration from Old System

### Old ColorTokens System

The old system used `ColorTokens` and `IAMConfiguration.updateColors()`:

```kotlin
// Old way
val colorTokens = createIAMColorTokens(isDark)
IAMConfiguration.updateColors(colorTokens)
```

### New IAMColorScheme System

The new system uses `IAMColorScheme` and automatic integration:

```kotlin
// New way - automatic integration
MeAppTheme {
    IAMTheme {
        IAMComponent() // Automatically gets app colors
    }
}

// Or manual integration
val appColorScheme = MeTheme.colorScheme
IAMColorIntegration.updateIAMColors(appColorScheme, isDarkMode)
```

## Troubleshooting

### Colors Not Working

1. Ensure IAM components are wrapped in `IAMTheme`
2. Check that the main app is using `MeAppTheme`
3. Verify IAM configuration is initialized

### Preview Issues

1. Use `IAMPreviewTheme` for previews
2. Ensure preview composables are properly wrapped
3. Check that all required colors are available

### Integration Problems

1. Verify app colors are being provided via `LocalIAMAppColors`
2. Check color mapping is working correctly
3. Ensure IAM components are within the app's theme hierarchy

## Examples

See `IAMThemeUsageExample.kt` for comprehensive usage examples and `AppIconColorExamples.kt` for specific component examples.

## Architecture

The IAM theme system follows a layered architecture:

```
Main App Theme (MeAppTheme)
    ↓
LocalIAMAppColors (CompositionLocal)
    ↓
IAMTheme (Composable Wrapper)
    ↓
IAMColorScheme (Color Resolution)
    ↓
IAM Components (Usage)
```

This architecture ensures:
- **Separation of Concerns**: App and IAM themes are independent
- **Flexibility**: IAM can work with or without app colors
- **Performance**: Colors are resolved efficiently with proper caching
- **Maintainability**: Clear interfaces between layers
