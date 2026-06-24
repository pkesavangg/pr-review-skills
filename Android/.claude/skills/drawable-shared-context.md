# Drawable Skill Shared Context
# Read this file at the start of both /drawable-import and /drawable-scan

## Key Paths

| Resource | Path |
|---|---|
| Drawables | `Android/app/src/main/res/drawable/` |
| Night drawables | `Android/app/src/main/res/drawable-night/` |
| Raw resources | `Android/app/src/main/res/raw/` |
| Night raw | `Android/app/src/main/res/raw-night/` |
| AppIcons.kt | `Android/app/src/main/java/com/dmdbrands/gurus/weight/resources/AppIcons.kt` |
| Project root | `Android/` (all Gradle commands run from here) |

## SVG Conversion — Gradle `convertSvg` Task

The project has a custom Gradle task in `app/build.gradle.kts` that uses Google's
`com.android.ide.common.vectordrawable.Svg2Vector` — the **exact same converter**
Android Studio's "Vector Asset" tool uses. No external npm tools (s2v, avocado) needed.

> **Note:** `Svg2Vector` is an internal AGP API (`com.android.ide.common`) — not part of
> AGP's public API. It may break on AGP major upgrades. If that happens, fall back to
> Android Studio's Vector Asset tool or the `vd-tool` CLI.

**Prerequisite check** (run before first use):

```bash
# Verify convertSvg task exists in the project
cd Android && ./gradlew tasks --group=assets 2>&1 | grep -q convertSvg || {
  echo "ERROR: convertSvg task not found in build.gradle.kts."
  echo "The task must be added to app/build.gradle.kts before SVG imports will work."
  exit 1
}
```

**Usage:**

```bash
# Single file
cd Android && ./gradlew :app:convertSvg -PsvgInput="/path/to/icon.svg"

# Folder (batch) with prefix
cd Android && ./gradlew :app:convertSvg -PsvgInput="/path/to/svgs" -PsvgPrefix="scale_"

# Custom output directory
cd Android && ./gradlew :app:convertSvg -PsvgInput="/path/to/svgs" -PsvgOutput="src/main/res/drawable-night"
```

**Options (via -P flags):**

- `svgInput`   — (required) path to a single SVG or a folder
- `svgOutput`  — (optional) output folder, defaults to `res/drawable/`
- `svgPrefix`  — (optional) prefix for output filenames (e.g. `"scale_"`, `"ic_"`)
- `svgReplace` — (optional) `"true"` to auto-delete matching `.png` (default: true)

**Why this over s2v:**

| Issue | `s2v` (npm) | `Svg2Vector` (Google) |
|---|---|---|
| `xlink:href` gradient inheritance | Produces `NaN` | Handles correctly |
| `<image>` embedded rasters | Empty output | Skips gracefully, converts rest |
| `<filter>`, `<mask>`, `<text>` | Fails entirely | Warns + converts what it can |
| Gradient transforms | Often broken | Same engine as Android Studio |

## Naming Conventions

Android drawables in this project follow TWO valid conventions — not one:

| Asset type | Convention | Example |
|---|---|---|
| Generic UI icons | `ic_<noun>[_variant]` | `ic_arrow_back`, `ic_close_outlined` |
| Domain-scoped assets | `<domain>_<noun>[_state][_sku]` | `wifi_ap_mode`, `error_t163_filled`, `scale_0412_user_name` |
| Brand/logo assets | `<brand>_logo`, `<brand>_banner` | `gg_logo`, `weight_gurus_banner` |
| Shape/background | `shape_<name>`, `bg_<name>` | `shape_pill_primary` |
| Setup/selector | `<feature>_<state>` | `wifi_ap_mode_filled`, `setup_complete_check` |

**Auto-fix transforms:**
```
HomeIcon.svg     → ask user: generic icon (→ ic_home_icon) or domain asset (→ enter domain)?
my-arrow.svg     → ic_my_arrow     (hyphens → underscores, add ic_ for generic)
WiFiSetup.png    → wifi_setup.png  (CamelCase → snake_case, domain detected)
BrandLogo.png    → brand_logo.png  (CamelCase → snake_case)
```

**Validation regex:** `^[a-z][a-z0-9_]+$` (no uppercase, no hyphens, no spaces)

**Do NOT blindly add `ic_` prefix** — only ~47% of drawables use it. Ask when ambiguous.

## File Routing

| Extension | Destination | R reference |
|---|---|---|
| `.svg` | `res/drawable/` (convert to XML first) | `R.drawable.*` |
| `.xml` | `res/drawable/` | `R.drawable.*` |
| `.png` | `res/drawable/` | `R.drawable.*` |
| `.webp` | `res/drawable/` | `R.drawable.*` |
| `.gif` | `res/raw/` | `R.raw.*` |
| `.json` (Lottie) | `res/raw/` | `R.raw.*` |

## AppIcons.kt Group Structure (actual, as of 2026-03-16)

```kotlin
object AppIcons {
  object Default { ... }       // Generic UI icons, brand logos
  object Outlined { ... }      // Outlined variants (ic_*_outlined)
  object Filled { ... }        // Filled/selected variants (ic_*_selected, ic_*_filled)
  object Selection { ... }     // Selection state icons (ic_circle_*)
  object Metrics { ... }       // Body metric icons (ic_body_fat, ic_bmi, etc.)
  object Connection { ... }    // Bluetooth, WiFi, AppSync icons
  object Milestone { ... }     // Achievement icons (streak, bolt)
  object Integrations { ... }  // Third-party logos + permission screens
  object Setup { ... }         // Scale setup + SKU-indexed GIFs (R.raw.*)
}
```

**Group selection guide:**
- New generic icon → `Default`
- New outlined variant → `Outlined`
- New filled/active variant → `Filled`
- New selection state → `Selection`
- New body metric icon → `Metrics`
- New connectivity icon → `Connection`
- New achievement icon → `Milestone`
- New third-party integration → `Integrations`
- New scale setup asset or R.raw.* → `Setup`

## AppIcons.kt Edit Rules

1. **Only modify `val` entries** — never touch `fun` entries (StepOnGif, PairModeGif, etc.)
2. **New R.raw.* entries belong in `Setup` group**
3. **PascalCase only** — `^[A-Z][A-Za-z0-9]+$`
4. **Atomic write** — write to `.tmp` file, then `mv` into place
5. **After every edit** — run `./gradlew :app:compileDebugKotlin` to verify syntax
6. **If compile fails** — diagnose and fix (do not blindly revert)

## Known Naming Violations in AppIcons.kt (fix during audit)

| Group | Current (wrong) | Correct |
|---|---|---|
| Default | `closeFilled` | `CloseFilled` |
| Default | `ggLogo` | `GgLogo` |
| Default | `profile` | `Profile` |
| Integrations | `My_Fitness_Pal` | `MyFitnessPal` |
| Integrations | `Health_Connect_Logo` | `HealthConnectLogo` |
| Integrations | `Health_Connect_Off` | `HealthConnectOff` |
| Integrations | `No_Permission` | `NoPermission` |
| Integrations | `Full_Permission` | `FullPermission` |
| Integrations | `HC_Homepage` | `HcHomepage` |
| Integrations | `User_Conflict` | `UserConflict` |
| Integrations | `Permission_Failed` | `PermissionFailed` |
| Setup | `wifiAPModeStepOn` | `WifiApModeStepOn` |

## Preview Convention

All previews must follow this exact pattern (per Android/CLAUDE.md):

```kotlin
@PreviewTheme
@Composable
fun <Name>Preview() {
    MeAppTheme {
        // content here
    }
}
```

`@PreviewTheme` expands to 6 `@Preview` annotations (Phone/Foldable/Tablet × Light/Dark).
Never use plain `@Preview` — always `@PreviewTheme`.

## Build Command

```bash
cd Android && ./gradlew assembleDebug
```

Always run this after any file mutation. If it fails, read the error and fix — do not skip.

## Coil GIF Setup (project uses Coil 3.x)

```kotlin
// Required dependency (already in project):
// io.coil-kt.coil3:coil-gif:<version>

// ImageLoader setup (already configured in project's Hilt module):
ImageLoader.Builder(context).components {
    if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory())
    else add(GifDecoder.Factory())
}.build()

// Usage in Compose:
AsyncImage(
    model = R.raw.my_animation,
    contentDescription = "...",
    // crossfade(false) to avoid interrupting animation loop
)
```

## Deletion Blocklist

Never offer to delete these regardless of unused analysis:
- `ic_launcher*`
- `ic_launcher_round*`
- `adaptive_icon*`
- `notification_icon*`
- Any file in `mipmap-*/`
