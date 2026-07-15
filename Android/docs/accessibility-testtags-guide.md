# Accessibility / testTags — Android Developer Guide

> Parent [MOB-1491](https://greatergoods.atlassian.net/browse/MOB-1491). This is the single
> source of truth for how we tag Android Compose controls for UI-test automation (Appium /
> UiAutomator2) and TalkBack. It is the Android mirror of
> [`iOS/docs/accessibility-identifiers-guide.md`](../../iOS/docs/accessibility-identifiers-guide.md).
> Linked from [`Android/CLAUDE.md`](../CLAUDE.md) (Definition of Done).

## Why testTags matter

QA automates every screen with **Appium / UiAutomator2**, which can only locate a control
through the Android view hierarchy — by `resource-id` if present, otherwise by fragile
`class[index]` / text locators that break on every redesign, localisation, or OS version.

Compose has no native `resource-id`. Instead:

1. A control is tagged with `Modifier.testTag("…")`, which publishes a **semantics** node.
2. `testTagsAsResourceId = true` on the enclosing window tells Compose to surface each
   descendant's `testTag` string **as its Android `resource-id`** — so Appium's
   `~snake_case` / `resource-id` selector finds it.

A stable snake_case tag that is **byte-for-byte identical to the iOS
`AccessibilityID`** lets QA write **one** selector that resolves the same control on both
platforms — and it improves TalkBack at the same time.

The reference implementation is the **Login** screen (pilot MOB-1492): `login_email_field`,
`login_password_field`, `login_submit_button`, … — each resolves to exactly one node.

## The catalog — single source of truth

All tag values live as constants in one object:

`Android/app/src/main/java/com/dmdbrands/gurus/weight/core/shared/utilities/testing/TestTags.kt`

- One nested `object` per feature/screen (e.g. `TestTags.Login`, `TestTags.Signup`,
  `TestTags.Landing`, …); more groups are added as screens are migrated under MOB-1491. Generic
  shared windows have `TestTags.Dialog` and `TestTags.BottomSheet`.
- Values are **snake_case** and **identical to the iOS `AccessibilityID+<Module>`** strings.
  Where a control exists only on Android, define it here and comment `// Android-defined; iOS to
  mirror`.
- **Always reference the constant** at the call site — never an inline string literal. This is
  what keeps Android and iOS in lockstep and lets a rename land in one place.

```kotlin
object TestTags {
  object Login {
    const val EmailField = "login_email_field"
    const val SubmitButton = "login_submit_button"
    // …
  }
}
```

## The 5 rules

1. **Every interactive control gets a stable tag** — buttons, inputs, toggles, tappable rows,
   tab items — sourced from a `TestTags` constant, never an inline literal.

2. **Tag at the call site with the right hook.** Most controls take a `Modifier.testTag(...)`;
   some shared components expose a dedicated tag param (see [The APIs](#the-apis) below). Do not
   push a per-screen tag down into a generic shared component (`SegmentButtonGroup`, `AppToggle`,
   `DateTimeInput`, list-row renderers) — tag at the screen/feature call site instead.

3. **Tags are single-sourced in `TestTags.kt`, snake_case, identical to iOS.** Add a new nested
   `object` when a screen is migrated; keep call sites as `TestTags.<Group>.<Name>`.

4. **Every separate window must opt into resource-id exposure.** `testTagsAsResourceId` resolves
   **per window** and does **not** cross `Dialog` / `Popup` / bottom-sheet boundaries. Apply
   `Modifier.exposeTestTagsAsResourceId()` at the root of every such window (see
   [Window-exposure gotcha](#the-window-exposure-gotcha)).

5. **Decorative elements are hidden; each tag resolves to exactly one node.** Purely decorative
   icons/images pass `contentDescription = null`. A tag applied to a container must not collide
   with another node — for repeated rows, suffix the tag with the item id (see
   [List rows](#list-rows-repeated-views)).

## The window-exposure gotcha

`Modifier.exposeTestTagsAsResourceId()` (in
`core/shared/utilities/testing/TestTagModifiers.kt`) sets `testTagsAsResourceId = true` and is
**gated to debug builds** — it is a no-op in release, so internal tag names never ship in the
production hierarchy.

Compose resolves the flag **once per window**. The root `setContent` is covered, but every
`Dialog`, `Popup`, and bottom sheet renders in its **own** semantics tree and needs its own call
at that window's root — otherwise a tagged control inside a dialog is invisible to Appium even
though the app root is exposed (MOB-1099, MOB-1503).

Already-covered windows: the app root (`MainActivity` / `MeApp`), and the shared window hosts —
`AppDialog`, `ModalDialog`, `AppBottomSheet`, `AppRadioGroupModal`, `AppSectionedRadioGroupModal`,
`TimePickerModal`, `AssignMeasurementDialog`, `LoaderCard`, `ToastHandler`. If you introduce a
**new** `Dialog`/`Popup` window, apply `.exposeTestTagsAsResourceId()` at its root.

### testTag vs. semantics (overlays)

`Modifier.testTag(...)` publishes a semantics node, which is normally enough. For a pure overlay
that is drawn without other semantics (e.g. the toast banner, MOB-1177), also attach a
`semantics {}` block so the node reliably participates in the tree and is TalkBack-friendly:

```kotlin
.testTag("toast_card")
.semantics {
    liveRegion = LiveRegionMode.Polite            // announced by TalkBack
    contentDescription = fullBannerText           // readable text on the node
}
```

## Derived control ids (clear / visibility toggle)

`AppInput` derives two child ids from the field's own tag, so you only declare the field:

- Clear ("x") button → `"<field_tag>_clear_button"`
- Password visibility toggle ("eye") → `"<field_tag>_visibility_toggle"`

e.g. `login_password_field` ⇒ `login_password_field_visibility_toggle`. The suffixes are the
constants in `TestTags.FieldSuffix` and mirror the iOS `AppInputField` derivation — do not
hand-write them.

## List rows (repeated views)

A list renders many rows, so a single shared tag would match many nodes. Suffix the row tag with
the item's stable id (never an inline literal):

```kotlin
Modifier.testTag("${TestTags.Landing.AccountCardRow}_${account.id}")   // account_card_row_<id>
```

Do this for each per-row control (row, delete, per-row action) so every row resolves to a unique
node. Per-provider/per-entity rows follow the same pattern (e.g.
`integration_row_<provider>`).

## The APIs

| Control | How to tag |
|---|---|
| Any composable (`AppButton`, `AppIconButton`, `Text`, container/root) | `Modifier.testTag(TestTags.X.Y)` — for `AppButton`/`AppIconButton` pass it as `modifier = Modifier.testTag(...)` |
| `AppInput` | dedicated `testTag = TestTags.X.Y` param (also derives `_clear_button` / `_visibility_toggle`) |
| `SettingsItem` (rows in `SettingsSection`) | set the model's `testTag = TestTags.X.Y` field |
| Dialog buttons via `BaseModal` / `ActionButton` | `ActionButton(testTag = TestTags.X.Y)`; container title via `titleTestTag` |
| Screen / container root | `Modifier.testTag(TestTags.X.ScreenRoot)` on the root `Column`/`LazyColumn`/`Box` (unlike iOS, a Compose container tag does not bleed onto children) |

`exposeTestTagsAsResourceId()` and the tag constants are all under
`core/shared/utilities/testing/`.

## Verifying on-device

Tags surface only in **debug** builds. To confirm a control is queryable:

```bash
# With the screen (or the banner/dialog) visible:
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
grep -i 'login_submit_button' ui.xml     # → resource-id present
```

Appium then selects it with `~login_submit_button` (accessibility-id) or by `resource-id`.

## Adding a new screen — checklist (Definition of Done)

- [ ] Screen root tagged with `Modifier.testTag(TestTags.<Group>.ScreenRoot)`.
- [ ] Every interactive control tagged from a `TestTags` constant (no inline string literals).
- [ ] Constants declared in a nested `object` in `TestTags.kt`, snake_case, **identical to the
      iOS `AccessibilityID`** where the control exists on both (else commented
      `// Android-defined; iOS to mirror`).
- [ ] Derived input ids left to `AppInput` (don't hand-write `_clear_button` /
      `_visibility_toggle`); repeated rows suffixed with the item id.
- [ ] Any **new** `Dialog`/`Popup`/bottom-sheet window applies
      `Modifier.exposeTestTagsAsResourceId()` at its root.
- [ ] Decorative icons pass `contentDescription = null`.
- [ ] Verified queryable via `uiautomator dump` (or the automation suite) in a debug build.
- [ ] `./gradlew assembleDebug` and `./gradlew detekt` pass.
