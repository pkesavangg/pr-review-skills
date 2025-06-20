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
    PrimaryDisabled,
    Secondary,

    // Support
    Overlay,
    ToastBackground,

    // Action
    PrimaryAction,
    PrimaryActionDisabled,
    SecondaryAction,
    SecondaryActionDisabled,
    TertiaryAction,
    TertiaryActionDisabled,
    Inverse,
    InverseDisabled,
    InverseSecondary,

    // Status
    Goal,
    Success,
    Danger,
    Streak,
    Utility,

    // Icon
    IconPrimary,
    IconPrimaryDisabled,
    IconSecondary,
    IconSecondaryDisabled,

    // Loading
    Loading,
    LoadingError,

    // Text
    Heading,
    Body,
    Subheading,
    Error,
    ErrorDisabled,

    // Brand
    MeAppPrimary,
    WgPrimary,
}
