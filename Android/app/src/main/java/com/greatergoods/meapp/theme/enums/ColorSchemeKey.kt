/**
 * Enum defining all semantic color roles for the application's color system.
 *
 * Each entry represents a semantic color role (background, support, action, text, icon, brand, etc.)
 * as specified in the design system. Used for mapping and retrieving colors in the theme.
 */
package com.greatergoods.meapp.theme.enums
enum class ColorSchemeKey {
    // Background
    Primary,
    Secondary,
    // Support
    Overlay,
    ToastBackground,
    // Action
    PrimaryAction,
    DisabledState,
    SecondaryAction,
    SecondaryDisabled,
    // Text
    Heading,
    Subheading,
    Error,
    Disabled,
    Inverse,
    InverseSecondary,
    // Icon
    Goal,
    Streak,
    Utility,
    // Brand
    Brand
}