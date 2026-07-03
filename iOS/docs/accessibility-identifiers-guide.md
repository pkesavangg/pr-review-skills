# Accessibility Identifiers — Developer Guide

> Epic [MOB-1131](https://greatergoods.atlassian.net/browse/MOB-1131). This is the
> single source of truth for how we tag iOS controls for UI-test automation and VoiceOver.
> Linked from [`iOS/CLAUDE.md`](../CLAUDE.md) (Definition of Done).

## Why identifiers matter

QA automates every screen with **Appium / XCUITest**, which can only locate a control through
the iOS **accessibility tree** — by `accessibilityIdentifier` if present, otherwise by label.
Without stable ids, tests fall back to fragile `type[index]` / class-chain / label-text
locators that break on every redesign, localisation, or iOS version. A stable snake_case id
that is **byte-for-byte identical to the Android `Modifier.testTag`** lets QA write **one**
`~snake_case` Appium selector that resolves the same control on both platforms — and it
improves VoiceOver at the same time.

The reference implementation is the **Login** screen (MOB-1132): `login_email_field`,
`login_password_field`, `login_submit_button`, etc. — each resolves to exactly one node.

## The 5 rules

1. **Every interactive control gets a stable id.** Buttons, `TextField`/`SecureField`,
   `Toggle`, tappable rows, and tab items carry an id via **`.appAccessibility(id:)`**, taken
   from an `AccessibilityID` constant — never an inline string literal.

2. **Screen/container roots use `.screenAccessibilityRoot(_:)` — never a bare
   `.accessibilityIdentifier(...)` on a container.** A bare identifier on a body/root
   container propagates onto every descendant that isn't its own clean leaf (Close/Help,
   legal text, …) and *overrides* their per-control ids. `screenAccessibilityRoot(_:)` pairs
   `.accessibilityElement(children: .contain)` with the id so it lands on a single container
   element and each child keeps its own id. This was the MOB-1132 gate failure — the SwiftLint
   rule `accessibility_id_on_screen_root` now blocks it.

3. **Ids are single-sourced, per module.** Constants live in
   `SharedAccessibility/AccessibilityID+<Module>.swift` (an `extension AccessibilityID`), so
   call sites stay `AccessibilityID.<constant>`. Values are snake_case and identical to the
   Android `testTag`.

4. **One id source feeds both targets.** `SharedAccessibility/` is a synchronized root group
   compiled into **both** the `meApp` app target and the `meAppUITests` target. XCUITest runs
   in a separate process and can't `import` app code, so the ids must be *compiled into* the
   test target — that's why there is no duplicate `UIAccessibilityID`. New files added to that
   folder auto-join both targets; do **not** add a separate copy on the test side.

5. **Decorative elements are hidden; each id resolves to exactly one node.** Purely decorative
   icons/images get `.accessibilityHidden(true)`. The `meAppUITests`
   `AccessibilityIDContractUITests` asserts each id resolves to exactly one element — extend it
   when a module gains ids.

## Derived control ids (clear / visibility toggle)

`AppInputField` / `BaseInputField` derive two ids from the field's own id, so you only declare
the field:

- Clear ("x") button → `"<field_id>_clear_button"`
- Password visibility toggle ("eye") → `"<field_id>_visibility_toggle"`

e.g. `login_password_field` ⇒ `login_password_field_visibility_toggle`. Mirror the same
derivation in the Android testTags.

## List rows (repeated views)

A list renders many rows, so a single shared id would match many nodes. Suffix the row id with
the item's stable identifier via a computed property (not an inline literal), e.g.
`account_card_row_<accountID>`, and make the row a **contained** element
(`.accessibilityElement(children: .contain)`) so it's one addressable tile whose child controls
keep their own ids.

## The APIs

```swift
// Leaf control:
Button("Log In") { … }
    .appAccessibility(id: AccessibilityID.loginSubmitButton)

// Screen / container root:
var body: some View {
    VStack { … }
        .screenAccessibilityRoot(AccessibilityID.loginScreenRoot)
}
```

Both live in `Core/Extensions/View + Extension.swift`.

## Adding a new screen — checklist (Definition of Done)

- [ ] Screen root tagged with `.screenAccessibilityRoot(_:)` (not a bare id).
- [ ] Every interactive control tagged with `.appAccessibility(id:)`.
- [ ] Ids declared in `SharedAccessibility/AccessibilityID+<Module>.swift`, snake_case.
- [ ] Values mirrored to the Android `Modifier.testTag`.
- [ ] Decorative icons `.accessibilityHidden(true)`.
- [ ] `AccessibilityIDContractUITests` extended for the new ids.
- [ ] `/review-accessibility` (or `/self-review`) reports no missing-id findings.
