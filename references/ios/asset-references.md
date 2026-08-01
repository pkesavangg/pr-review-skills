# iOS Asset References — never name an asset with a raw string

An asset-catalog name written as a string literal is the most silent failure mode in a SwiftUI app. `Image("icon.cirelce")` **compiles**, **links**, **passes every unit test**, and then renders *nothing* on the user's screen. There is no crash, no warning, no log line — just a hole in the UI that only a human looking at the right screen will notice.

The fix is the one the codebase already applies to colors: the literal lives in **exactly one place** — a token file that defines a named constant — and every call site references that constant. A typo then becomes a compile error, a rename is one edit, and the name is documented by the constant next to it.

```swift
Image("icon.cirelce")     // ✗ typo compiles, renders nothing
Image(AppAssets.circle)   // ✓ typo is a compile error
```

Severity uses the orchestrator's taxonomy. **Flag on `+`/modified lines only.** If a repo `CLAUDE.md` / design-system doc prescribes a different asset-reference mechanism, prefer it and skip the conflicting rule.

> **This file owns the asset-literal rule.** `vendored/swiftui-pro/references/api.md` mentions generated symbols in one conditional bullet, which the orchestrator re-classifies as a stylistic `P2`/`Nit`. That undersells it — a mistyped asset name is a user-visible defect, not a style preference. When both fire at the same `file:line`, keep **this** finding and drop the vendored one.

---

## The project's established pattern (cite it by name)

Both apps already centralize design values under `Theme/Tokens/`:

| Token file | Owns |
|---|---|
| `Theme/Tokens/ColorTokens.swift` | every `Color("…")` asset-catalog name (`ColorTokens.theme500`, `ColorTokens.neutral0`) |
| `Theme/Tokens/Spacing.swift`, `Typography.swift`, `IconSize.swift`, `BorderRadius.swift`, `Elevation.swift`, `Motion.swift`, `Layout.swift` | the rest of the design system |

**Colors are done; images are the gap.** Neither app has an `ImageTokens.swift`, so image names are still typed as literals at each call site. The fix for an image literal is therefore *"add it to `Theme/Tokens/ImageTokens.swift`, mirroring `ColorTokens`"* — not "invent a new mechanism".

```swift
// Theme/Tokens/ImageTokens.swift — the one place an image name may be a string
import SwiftUI

enum ImageTokens {
    // MARK: - Device
    static let kettleHero = Image("kettle_hero")
    static let bluetooth  = Image("bluetooth")
}
```

```swift
// call sites — no literal anywhere
ImageTokens.bluetooth
ImageTokens.kettleHero.resizable().scaledToFit()
```

If the project has **Xcode 15+ generated asset symbols** enabled, prefer those instead — they are compiler-generated from the catalog, so they cannot drift from it at all:

```swift
Image(.kettleHero)          // generated ImageResource symbol
Color(.themePrimary)        // generated ColorResource symbol
```

Use generated symbols when available; otherwise the `Theme/Tokens/` enum. Never a bare literal at a call site.

---

## P1 — Asset referenced by a raw string literal at a call site

Every literal call site is an independent chance to misspell the name, and a shared liability when the asset is renamed in the catalog — the rename compiles everywhere and breaks everywhere.

```swift
// DeviceRowCard.swift, ConnectivityChip.swift, ControlCenterBluetoothIllustration.swift
Image("bluetooth")        // ← the same literal, retyped in three files

// KettleStatusHeader.swift, DeviceSettingsView.swift, KettleCardContent.swift
Image("kettle_hero")      // ← and this one in three more
```

**Sniff.** On `+` lines, a string literal passed as an asset name: `Image("…")`, `Color("…")`, `UIImage(named: "…")`, `UIColor(named: "…")`, `NSImage(named:)`, `Font.custom("…")`, `Bundle.main.url(forResource: "…")` for a bundled media file — **outside** a `Theme/Tokens/*.swift` (or equivalently-designated) file. A literal appearing in ≥2 files is the strongest signal.

**Fix.** Add the constant to the matching token file once, then reference it:

```swift
// before
Image("bluetooth")
// after
ImageTokens.bluetooth
```

**Do NOT flag:**
- The **token file itself** — `Theme/Tokens/ColorTokens.swift` is precisely where the literal is supposed to live, exactly once. Flagging `static let theme50 = Color("Theme/theme-50")` inside `ColorTokens.swift` is backwards.
- `Image(systemName: "…")` — SF Symbols are Apple's namespace, not the app's asset catalog (see the `Nit` below for the repeated-symbol case).
- A name that genuinely arrives at runtime (from an API response, a deep link) — that can't be a compile-time constant. Do check it's validated before use.
- Preview-only or test-only code where a literal keeps the fixture readable, if the repo's convention allows it.

---

## P1 — Asset name doesn't exist in any asset catalog

This is the `Image("icon.cirelce")` case, and unlike most review findings it is **mechanically verifiable** — so verify it rather than guessing.

**Sniff.** For each asset literal on a `+` line, confirm a matching entry exists in the project's catalogs:

```bash
# an image named "kettle_hero"
find . -name "*.xcassets" -type d -exec find {} -name "kettle_hero.imageset" \; | head
# a color named "Theme/theme-50"  (a "/" in the name is a catalog folder)
find . -name "*.xcassets" -type d -exec find {} -path "*Theme/theme-50.colorset" \; | head
```

No match → the asset does not exist, and the view will render an empty space at runtime.

**Fix.** Correct the spelling to the real catalog entry, or add the missing asset to the catalog in this same PR. Then apply the P1 rule above so the name never has to be typed again. Note in the comment which catalog you searched, so the author can tell a typo from a genuinely-missing asset:

> `P1 — Asset "icon.cirelce" not found in any .xcassets · This renders as an empty view at runtime with no error. Did you mean "icon_circle"? Add the constant to Theme/Tokens/ImageTokens.swift and reference ImageTokens.circle instead of the literal.`

**Do NOT flag** when the catalogs aren't in the diff and you couldn't search them (a partial checkout) — say so in the summary instead of asserting a miss you didn't verify.

---

## P2 — Asset name isn't meaningful, or breaks the catalog's convention

An asset name is an API used by designers, QA, and both platforms. `Group 3`, `image1`, `asset_2_copy`, `Frame 27`, and `icon_final_v2` are Figma-export and iteration artifacts — they say nothing about what the asset *is*, so nobody can find or reuse them and the catalog grows duplicates.

| Bad | Why | Good |
|---|---|---|
| `Group 3`, `Frame 27` | Figma layer name, leaked into the app | `ic_bluetooth` |
| `image1`, `img_2` | numbered, meaningless | `kettle_hero` |
| `icon_final_v2`, `bg_copy` | iteration noise baked into the name | `ic_circle`, `bg_card` |
| `icon.cirelce` | misspelled, and `.` isn't valid in the Android twin | `ic_circle` |
| `BlueToothICON` | inconsistent casing against the catalog | `ic_bluetooth` |

**Convention.** Match the catalog's existing dominant style — in these apps that is lowercase `snake_case` with a role prefix where one is in use (`ic_` icon, `bg_` background, `illus_` illustration), and a `Folder/name` path for grouped colors (`Theme/theme-500`). Name the asset for **what it depicts or does**, never for where it came from or which iteration it is.

**Sniff.** On `+` lines: an asset name (in a literal *or* a newly added catalog entry) containing a space, a `.`, an uppercase letter where the catalog is snake_case, a bare trailing digit, or the words `copy`, `final`, `new`, `v2`, `Group`, `Frame`, `Rectangle`, `Vector`, `Union`, `Mask`.

**Fix.** Rename the catalog entry and the constant together. Prefer the same name as the existing Android drawable when one exists (see below).

---

## P2 — Constant name doesn't match what the asset actually is

The whole point of the token is that the name is trustworthy. A constant whose name disagrees with its asset is worse than a literal, because now the call site *reads* correctly while doing the wrong thing.

```swift
enum ImageTokens {
    static let bluetooth = Image("kettle_hero")   // ✗ name says one thing, asset is another
    static let icon1     = Image("ic_bluetooth")  // ✗ meaningless constant over a fine asset
}
```

**Sniff.** On `+` lines in a token file: a `static let <name> = Image("<assetName>")` / `Color("<assetName>")` where `<name>` and `<assetName>` don't describe the same thing (compare after normalizing case, `_`, and any role prefix); or a constant named `icon1` / `image2` / `temp` / `asset`.

**Fix.** Rename the constant to lowerCamelCase of the asset's meaning — `ic_bluetooth` → `bluetooth`, `kettle_hero` → `kettleHero`. Keep the mapping mechanical so a reader can predict either name from the other.

---

## P2 — Asset name diverges from its Android twin

The same artwork shipped under `kettle_hero` on iOS and `hero_kettle` on Android means design hand-off, QA scripts, and docs need a translation table, and a future shared design-system export can't match them up. SageApp already ships `kettle_hero.png` under both `Assets.xcassets` and `res/drawable/` — keep that parity.

**Sniff.** A new asset added on one platform in this PR whose Android/iOS counterpart exists under a different name. Check `**/res/drawable*/` for the Android side, `**/*.xcassets` for iOS.

**Fix.** Use one name on both platforms — `snake_case`, since Android resource names *must* be `[a-z][a-z0-9_]*` and iOS has no such restriction. That constraint makes Android the naming authority: pick a name Android can accept and iOS will always be able to match it. This mirrors the `accessibilityIdentifier` ↔ `testTag` parity rule in [`accessibility-identifiers.md`](accessibility-identifiers.md).

**Do NOT flag** a genuinely platform-specific asset (an iOS-only widget background, an Android notification icon) — parity only applies where both platforms ship the same artwork.

---

## Nit — Repeated SF Symbol string literal

`Image(systemName: "gear")` isn't an asset-catalog reference, so the rules above don't apply — but the same symbol name retyped across many views has the same rename and typo exposure, and a mistyped SF Symbol also renders as nothing.

**Sniff.** The same `systemName:` literal on `+` lines in ≥3 files.

**Fix.** Give it a constant alongside the image tokens (`enum SymbolTokens { static let settings = "gear" }`), or on iOS 17+ prefer the type-safe `Image(systemName:)` alternatives your minimum deployment target allows. Low priority — Apple's symbol names are stable and centrally documented, so this is convenience, not correctness.
