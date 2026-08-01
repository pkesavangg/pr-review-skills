# Compose Asset & Resource References — keep the compiler in the loop

Android's `R` class already does most of this job for you: `painterResource(R.drawable.ic_circle)` is compile-checked, so a typo is a build error rather than a blank screen. The findings here are the places where code **steps around** that guarantee — a runtime string lookup, a hardcoded asset path, a literal that should have been a resource — plus the naming rules that keep the resource set usable by both platforms.

The iOS counterpart is [`../ios/asset-references.md`](../ios/asset-references.md), where the risk is much higher (`Image("icon.cirelce")` compiles and silently renders nothing). The two files share one standard: **an asset is referenced by a typed symbol, and its name is meaningful, valid, and the same on both platforms.**

Severity uses the orchestrator's taxonomy. **Flag on `+`/modified lines only.** If a repo `CLAUDE.md` / design-system doc prescribes different conventions, prefer them and skip the conflicting rule.

---

## P1 — Resource looked up by a runtime string (`getIdentifier`)

`Resources.getIdentifier("ic_circle", "drawable", packageName)` throws away every guarantee `R` gives you. It isn't compile-checked, so a typo returns `0`; it's slow (a reflective table scan); and — the real killer — **R8/resource shrinking can't see the reference**, so the drawable gets stripped from the release build and the screen that worked in debug is blank in production.

```kotlin
// ✗ compiles, works in debug, blank in a shrunk release build
val id = context.resources.getIdentifier(iconName, "drawable", context.packageName)
Image(painter = painterResource(id), contentDescription = null)
```

**Sniff.** `getIdentifier(` on `+` lines, in any form — `resources.getIdentifier`, `Resources.getSystem().getIdentifier`.

**Fix.** Map the dynamic key to typed ids explicitly, so every drawable is a real reference the compiler and the shrinker can both see:

```kotlin
@DrawableRes
private fun iconFor(kind: DeviceKind): Int = when (kind) {
    DeviceKind.KETTLE -> R.drawable.ic_kettle
    DeviceKind.SCALE  -> R.drawable.ic_scale
}

Image(painterResource(iconFor(device.kind)), contentDescription = null)
```

The `when` is exhaustive over the enum (see [`../code-standards/kotlin.md`](../code-standards/kotlin.md)), so adding a device kind becomes a compile error instead of a missing icon. If the set genuinely comes from a server, keep the mapping table in code and fall back to a known-good default — never reflect.

**If shrinking must keep a dynamically-named set**, that requires a `keep` rule in `proguard-rules.pro` — say so in the finding, because the fix isn't complete without it.

---

## P1 — Hardcoded asset path or file name string

A path into `assets/` or an external file is not checked by anything: no compile error, no lint, and a rename or a folder move fails only when that screen is opened.

```kotlin
AsyncImage(model = "file:///android_asset/images/kettle_hero.png", contentDescription = null)  // ✗
context.assets.open("config/devices.json")                                                     // ✗ retyped per call site
```

**Sniff.** On `+` lines: a string literal containing `file:///android_asset/`, `assets/`, or a bare `"*.png"` / `"*.json"` / `"*.svg"` / `"*.webp"` file name passed to `assets.open`, `AsyncImage(model =`, Coil/Glide loaders, or a `File(...)` constructor.

**Fix.** Prefer a real resource (`R.drawable.kettle_hero` via `painterResource`) — resources are typed, density-aware, and shrinker-visible; `assets/` is for things that genuinely must stay as raw files. Where `assets/` is required, put the path in one `const val` next to the loader so it's typed once:

```kotlin
private const val DEVICE_CONFIG_ASSET = "config/devices.json"
```

---

## P2 — Hardcoded user-facing string instead of `stringResource`

A literal in `Text("Save")` can't be translated, can't be reused, and can't be checked by the localization tooling — the string ships in English to every locale. This is the Android form of "don't put static strings in the UI".

```kotlin
Text("Kettle is heating")                       // ✗ untranslatable
Button(onClick = onSave) { Text("Save") }       // ✗
```

**Sniff.** A string literal on a `+` line passed to `Text(`, `Button` content, `label =`, `placeholder =`, `title =`, `contentDescription =`, `TextField(label =)`, or any `AnnotatedString` builder — in a `@Composable`.

**Fix.**

```kotlin
Text(stringResource(R.string.kettle_status_heating))
Button(onClick = onSave) { Text(stringResource(R.string.action_save)) }
```

**Do NOT flag:** debug/log text, `@Preview` sample data, test code, a formatted value that is already a `stringResource` with arguments, or a genuinely non-linguistic string (a units symbol the design says is fixed). Where a repo hasn't started localizing at all, raise it **once** in the summary rather than per call site — the current suite has ~26 hardcoded `Text("…")` against 1 `stringResource`, so a per-line sweep would bury every other finding.

---

## P2 — Hardcoded color / dimension where a theme token exists

`Color(0xFF0A84FF)` or `16.dp` inline duplicates a decision the design system already owns, and it can't respond to dark mode or a theme change.

```kotlin
Surface(color = Color(0xFF0A84FF)) { … }        // ✗ won't follow the theme, breaks in dark mode
Modifier.padding(16.dp)                          // ✗ if the project has a Spacing scale
```

**Sniff.** On `+` lines: a `Color(0x…)` / `Color(red =` literal, or a bare `.dp` / `.sp` numeric outside the theme/token files, in a project that has a `MaterialTheme` color scheme or a token object.

**Fix.** `MaterialTheme.colorScheme.primary`, `colorResource(R.color.theme_500)`, or the project's token (`Spacing.md`). The iOS side has the same rule via `ColorTokens` — see [`../ios/asset-references.md`](../ios/asset-references.md). **Do NOT flag** the theme/token definition files themselves, or a one-off value the design explicitly specifies as literal.

---

## P2 — Resource name isn't meaningful or breaks the `res/` convention

Android enforces the *syntax* of resource names — `[a-z][a-z0-9_]*`, so `icon.cirelce` and `Group 3` won't even build — but nothing enforces that the name is **useful**. Figma-export names and numbered names leak in constantly and make the drawable folder unsearchable, which is how the same icon gets added three times.

| Bad | Why | Good |
|---|---|---|
| `group_3`, `frame_27`, `vector`, `union` | Figma layer names | `ic_bluetooth` |
| `image1`, `img_2`, `icon_3` | numbered, meaningless | `kettle_hero` |
| `ic_final_v2`, `bg_copy`, `ic_new` | iteration noise | `ic_circle`, `bg_card` |
| `circle` (an icon), `kettle` (a photo) | no role prefix, so it sorts nowhere | `ic_circle`, `img_kettle_hero` |

**Convention** — the one this suite already follows in `res/drawable/`: lowercase `snake_case` with a role prefix — `ic_` icon (`ic_notif_bolt`, `ic_sage_brand`), `bg_` background/shape (`bg_notification_kettle_card`), `illus_` illustration; layout `activity_`/`fragment_`; string ids namespaced by feature and role (`action_save`, `kettle_status_heating`). Name the resource for **what it depicts or does**.

**Sniff.** A newly added file under `res/drawable*/`, `res/layout/`, `res/raw/`, or a new `<string name="…">` on `+` lines whose name: is numbered, contains `copy`/`final`/`new`/`v2`/`temp`, matches a Figma default (`group_`, `frame_`, `rectangle_`, `vector`, `union`, `mask`, `path_`), or lacks the role prefix its siblings all use.

**Fix.** Rename the resource file and every `R.` reference together (the IDE's rename refactor handles both). Do it in the PR that introduces it — renaming later is a much wider diff.

---

## P2 — Resource name diverges from its iOS twin

The same artwork shipped as `kettle_hero` on Android and `heroKettle` on iOS forces a translation table on design hand-off, QA scripts, and docs, and blocks a shared design-system export. SageApp already ships `kettle_hero` under both `res/drawable/` and `Assets.xcassets` — that parity is the target state, not an accident.

**Sniff.** A new drawable added in this PR whose iOS counterpart exists under a different name — check `**/*.xcassets` for a `<name>.imageset`.

**Fix.** Use one `snake_case` name on both platforms. Android's `[a-z][a-z0-9_]*` restriction is the tighter one, so **Android is the naming authority**: pick a name Android accepts and iOS can always match it. Mirrors the `testTag` ↔ `accessibilityIdentifier` parity rule in [`accessibility.md`](accessibility.md) and [`../ios/accessibility-identifiers.md`](../ios/accessibility-identifiers.md).

**Do NOT flag** a genuinely platform-specific asset (an adaptive-icon layer, a notification small-icon, an iOS widget background).

---

## Nit — `painterResource` called inside a loop or a hot composable body

`painterResource` reads and decodes on the composition thread. Calling it per item inside a `LazyColumn`'s `items { }` body re-resolves the same drawable on every recomposition.

**Sniff.** `painterResource(` inside an `items {` / `itemsIndexed {` lambda or a composable that recomposes frequently, on `+` lines.

**Fix.** Hoist it above the list, or pass the `@DrawableRes Int` down and resolve once. Related: [`recomposition.md`](recomposition.md) — if that rule already fired at the same `file:line`, post one finding.
