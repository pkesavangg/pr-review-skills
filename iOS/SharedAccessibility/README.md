# SharedAccessibility

Single source of truth for `accessibilityIdentifier` strings, **compiled into both**
the `meApp` app target and the `meAppUITests` target.

## Why a top-level shared folder (and not `meApp/Core/Utilities/Accessibility/`)?

XCUITest runs the test bundle in a **separate process** and cannot `import` the app
target's code — this is exactly why the old `UIAccessibilityID` had to duplicate the
app's `AccessibilityID`. To keep **one** source with **no duplicate**, the id files must
be *compiled into both targets*. Xcode 16 synchronized root groups map one folder to a
target, and folders can't overlap, so a shared folder sits outside the app's synced
root and is referenced by both targets' `fileSystemSynchronizedGroups`.

## Layout

- `AccessibilityID.swift` — base `enum AccessibilityID` + root/shared ids.
- `AccessibilityID+<Module>.swift` — one `extension AccessibilityID` per feature module.
  Call sites stay `AccessibilityID.<constant>` on both the app and test sides.

## Rules (full guide: `iOS/docs/accessibility-identifiers-guide.md`, MOB-1144)

1. Screen/container roots use `.screenAccessibilityRoot(_:)` — never a bare
   `.accessibilityIdentifier(...)` on a container (it bleeds onto children — MOB-1132).
2. Leaf controls use `.appAccessibility(id:)`.
3. Values are snake_case and identical to the Android `Modifier.testTag`.
4. Decorative icons are `accessibilityHidden(true)`.
5. Every id must resolve to exactly one element (enforced by the `meAppUITests`
   `AccessibilityIDContractUITests` contract test).
