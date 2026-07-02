//
//  AccessibilityID.swift
//  meApp — Shared Accessibility (single source of truth)
//
//  These identifiers are compiled into BOTH the `meApp` app target and the
//  `meAppUITests` target (this folder is a synchronized root group referenced by
//  both — see SharedAccessibility/README.md). XCUITest runs in a separate process
//  and cannot `import` app code, so the ids must be *compiled into* the test target
//  rather than imported; a shared folder is how we keep one source with no duplicate.
//
//  Values are snake_case and MUST stay byte-for-byte identical to the Android
//  `Modifier.testTag` strings so a single Appium `~snake_case` selector resolves a
//  control on both platforms (and it improves VoiceOver at the same time).
//
//  Conventions (see iOS/docs/accessibility-identifiers-guide.md):
//   • Tag leaf controls with `.appAccessibility(id:)`.
//   • Tag a screen/container root with `.screenAccessibilityRoot(_:)` — NEVER a bare
//     `.accessibilityIdentifier(...)` on a container (it bleeds onto children; MOB-1132).
//   • Derived control ids: `<field_id>_clear_button`, `<field_id>_visibility_toggle`.
//   • Per-module ids live in `AccessibilityID+<Module>.swift` extensions so every call
//     site stays `AccessibilityID.<constant>`.
//
//  A `meAppUITests` contract test asserts every declared id resolves to exactly one
//  element on its screen (that assertion is what caught the MOB-1132 root-id bleed).

enum AccessibilityID {
    // MARK: - Root Screens
    static let landingScreenRoot = "landing_screen_root"
    static let loginScreenRoot = "login_screen_root"

    // MARK: - Shared components (used across features)
    static let accountCardLoggedOutLabel = "account_card_logged_out_label"
}
