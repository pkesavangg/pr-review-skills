# Accessibility Identifiers — automation-facing tagging (MOB-1131)

House-specific contract for tagging iOS controls so **Appium / XCUITest** can locate them. This is
**not** the VoiceOver-UX concern that `swiftui-pro/references/accessibility.md` covers (labels,
Dynamic Type, hit targets) — it is about the *stable `accessibilityIdentifier`* QA automates
against. Single source of truth in the reviewed repo: `iOS/docs/accessibility-identifiers-guide.md`.

The contract in one line: **every interactive control carries a stable snake_case id that is
byte-for-byte identical to its Android `Modifier.testTag`**, so one `~snake_case` Appium selector
resolves the same control on both platforms. Ids come from an `AccessibilityID` constant (never an
inline literal) and are applied through three sanctioned APIs — nothing else:

| API | Use for |
|---|---|
| `.appAccessibility(id:label:)` | an interactive **leaf** control (button, `TextField`/`SecureField`, `Toggle`, tappable row, tab item) |
| `.screenAccessibilityRoot(_:)` | a **screen / container root** (pairs `.accessibilityElement(children: .contain)` with the id so it lands on one element and children keep their own ids) |
| `.accessibilityIdentifierIfPresent(_:)` | a **shared component** whose id is optional — applies nothing when `nil` instead of stamping an empty id |

**What the SwiftLint gate already catches — do NOT re-flag these** (they fail CI on their own; the
`.swiftlint.yml` custom rules `accessibility_id_on_screen_root` and
`accessibility_identifier_string_literal` are mechanical regex rules):

- a **bare** `.accessibilityIdentifier(AccessibilityID.…ScreenRoot)` on a container → the lint error.
- a **bare** `.accessibilityIdentifier("string literal")` → the lint warning.

These rules cover what a regex **can't** see: a control that has no id at all, an id that won't
resolve to a single node, and an id that silently diverges from its Android twin. The iOS repo's own
`review-accessibility` skill runs the same semantic checks in-repo; these rules let the PR reviewer
enforce the contract from outside the repo.

---

## P1 — Interactive control added with no automation id

```swift
// added in this PR — no way for Appium to address it
Button("Log In") { viewModel.submit() }
SecureField("Password", text: $password)
```

An interactive control with no `.appAccessibility(id:)` (or, for a shared component, no id threaded
through to `.accessibilityIdentifierIfPresent(_:)`) is **unreachable by a stable selector** — QA
falls back to `type[index]` / label-text locators that break on every redesign or localisation. This
is the DoD gate for a new screen.

**Sniff.** For each changed `.swift` view file, find controls **added in DIFF** that are interactive
— `Button`, `TextField`, `SecureField`, `Toggle`, `Picker`, `NavigationLink` with an action, a row
with `.onTapGesture`, or a `.tag(...)` tab item — and check whether the same element chain carries
`.appAccessibility(id:)` / `.accessibilityIdentifier(...)` / `.accessibilityIdentifierIfPresent(...)`.
Flag when none is present.

**Carve-outs — do NOT flag:**

- A control rendered by a **shared component** (`AppInputField`, `BaseInputField`, nav-bar header)
  that already accepts and applies an id — the id lives at the call site, not the component.
- Derived ids the framework adds automatically: the field's clear (`_clear_button`) and visibility
  (`_visibility_toggle`) buttons — declaring the field's id is enough.
- Purely presentational text/decorative content (not interactive).

**Fix.**

```swift
Button("Log In") { viewModel.submit() }
    .appAccessibility(id: AccessibilityID.loginSubmitButton)
```

Declare `loginSubmitButton` in `SharedAccessibility/AccessibilityID+<Module>.swift` and mirror the
value in the Android `testTag`.

---

## P1 — Repeated row / list item shares one static id

```swift
ForEach(accounts) { account in
    AccountCard(account)
        .appAccessibility(id: AccessibilityID.accountCardRow)   // matches N nodes
}
```

A single id inside a `ForEach` / `List` resolves to **many** elements, so a selector can't
disambiguate and the `AccessibilityIDContractUITests` "resolves to exactly one element" assertion
fails.

**Sniff.** For a `.appAccessibility(id:)` / `.accessibilityIdentifier(...)` **added in DIFF** whose
enclosing scope is a `ForEach` / `List` / `LazyVStack` row builder, flag when the id is a bare
constant with no per-item suffix.

**Fix.** Suffix the id with the item's stable identifier via a computed property (not an inline
literal) and make the row a contained element:

```swift
AccountCard(account)
    .appAccessibility(id: AccessibilityID.accountCardRow(account.id))   // account_card_row_<id>
    .accessibilityElement(children: .contain)
```

---

## P1 — iOS id diverges from the Android `testTag`

The entire point of the contract is **one** `~snake_case` selector resolving on both platforms. An
iOS id of `emailField` (or `login-email`) against an Android testTag of `login_email_field` forces
QA to branch the selector and defeats the shared-selector design.

**Sniff.** Flag an id value that is **not snake_case** (camelCase, kebab-case, or a UI string). When
the PR **also touches the Android side** — a `Modifier.testTag(...)` for the same control in the diff
— compare the two literals and flag any mismatch.

**Carve-out — do NOT flag:** a snake_case id whose Android twin is **not visible in this PR**. Note
it as an unverified assumption ("confirm the Android `testTag` matches") rather than a finding — don't
invent a testTag value you can't see.

**Fix.** Rename to the shared snake_case value used by both platforms (`login_email_field`).

---

## P2 — Screen / container root not tagged with `.screenAccessibilityRoot(_:)`

A new screen whose body root has no `.screenAccessibilityRoot(_:)` leaves the screen container
un-addressable (Appium can't scope a search to the screen) and misses the DoD checklist item.

**Sniff.** For a **new** screen-level `View` (a type whose name ends `Screen`/`View` and whose `body`
returns a top-level container — `NavigationStack`, `VStack`, `ScrollView`, `Form`), flag when the
root chain has no `.screenAccessibilityRoot(_:)`.

**Carve-out — do NOT flag:** small reusable subviews and cells (they aren't screen roots), or a body
that is itself a single already-tagged shared component.

**Fix.**

```swift
var body: some View {
    VStack { … }
        .screenAccessibilityRoot(AccessibilityID.loginScreenRoot)
}
```

Never reach for a bare `.accessibilityIdentifier(...)` on the container — that bleeds the id onto
every child and is the MOB-1132 gate bug (and the SwiftLint gate blocks it).

---

## P2 — Id passed as a string literal instead of an `AccessibilityID` constant

```swift
Button("Save") { … }
    .appAccessibility(id: "save_button")   // literal — not shared with meAppUITests
```

Ids are single-sourced so the app target and the `meAppUITests` target compile from the **same**
constant (XCUITest can't `import` app code). A literal here won't be shared, drifts silently, and
sidesteps the contract test. The SwiftLint regex catches a bare `.accessibilityIdentifier("…")` but
**not** a literal handed to `.appAccessibility(id:)` — this rule closes that gap.

**Sniff.** Flag a string-literal argument to `.appAccessibility(id:)`, `.screenAccessibilityRoot(_:)`,
or `.accessibilityIdentifierIfPresent(_:)`. (A literal on a bare `.accessibilityIdentifier(...)` is
already the lint warning — don't double-report it.)

**Fix.** Add the constant to `SharedAccessibility/AccessibilityID+<Module>.swift` and reference
`AccessibilityID.saveButton`.

---

## P2 — Optional id applied as `id ?? ""` instead of `accessibilityIdentifierIfPresent(_:)`

```swift
.accessibilityIdentifier(automationID ?? "")   // stamps an EMPTY id on un-opted screens
```

An empty identifier pollutes the accessibility tree of every screen that passes `nil`. The
`accessibilityIdentifierIfPresent(_:)` extension exists precisely to apply nothing in that case.

**Sniff.** Find `.accessibilityIdentifier(` whose argument is `<expr> ?? ""` (or `?? ""` anywhere in
the argument).

**Fix.** `.accessibilityIdentifierIfPresent(automationID)`.

---

## Nit — New `AccessibilityID` constants not covered by the contract test

`AccessibilityIDContractUITests` asserts each id resolves to exactly one element; it's the guardrail
that keeps ids unique. When a PR adds constants to `SharedAccessibility/AccessibilityID+*.swift` but
touches no test, the new ids go unguarded.

**Sniff.** New entries in a `SharedAccessibility/AccessibilityID+*.swift` file in the diff, with no
change to `AccessibilityIDContractUITests` (or the module's contract test) in the same PR.

**Fix.** Extend `AccessibilityIDContractUITests` with the new ids.

---

**De-dup with swiftui-pro.** Decorative images (`Image(...)` that should be `Image(decorative:)` /
`.accessibilityHidden(true)`) are a **swiftui-pro `accessibility.md`** finding — don't raise a second
comment here. The de-dup against swiftui-pro and prior reviewer comments happens at Step 4a.4.

## Output

For each finding, emit one line:

```
[<file>:<line>] <severity> — Accessibility IDs — <one-line rule> · <one-sentence fix>
```
