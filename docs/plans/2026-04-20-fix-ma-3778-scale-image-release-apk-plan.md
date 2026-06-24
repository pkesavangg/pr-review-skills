---
title: MA-3778 Fix scale drawables stripped from release APK by resource shrinker
type: fix
status: active
date: 2026-04-20
origin: docs/brainstorms/2026-04-20-ma-3778-scale-image-missing-brainstorm.md
---

# MA-3778 Fix scale drawables stripped from release APK by resource shrinker

## Overview

Scale and BPM-monitor device imagery is missing across every screen of signed release APKs (MA-3778). The drawables render correctly in debug builds but disappear in release. Root cause is R8's resource shrinker (`isShrinkResources = true`) stripping drawables that are referenced only via `Resources.getIdentifier()` — R8 cannot trace runtime name lookups, so it considers those drawables unused. Fix is a single new `res/raw/keep.xml` declaring `tools:keep` wildcards for `@drawable/scale_*` and `@drawable/monitor_*`, plus a one-line correction to the misleading "R8/ProGuard-safe" comment at [ScaleImageHelper.kt:9](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/helper/ScaleImageHelper.kt#L9) that contributed to this bug shipping.

## Problem Statement / Motivation

- **Symptom:** User confirmed "scale image missing across all devices" in the signed release APK built on 2026-04-20 (Jira [MA-3778](https://greatergoods.atlassian.net/browse/MA-3778)). Affects every scale SKU and every BPM monitor, on every screen that renders a device image.
- **Root cause:** [ScaleImageHelper.kt:16](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/helper/ScaleImageHelper.kt#L16) resolves drawables by name with `context.resources.getIdentifier("${prefix}_$sku", "drawable", context.packageName)`. The release build block at [app/build.gradle.kts:66-67](Android/app/build.gradle.kts#L66-L67) sets both `isMinifyEnabled = true` and `isShrinkResources = true`. R8's resource shrinker does not statically resolve `getIdentifier()` arguments, so every `scale_*` and `monitor_*` drawable that lacks a static `R.drawable.*` reference is stripped from the APK. `getIdentifier()` then returns `0` and the helper silently falls back to `AppIcons.Default.ScalePlaceholder`.
- **Why this shipped:** MA-3018 refactored `ScaleImageHelper` from Java reflection on `R.drawable` to `Resources.getIdentifier()` to satisfy R8's **code** shrinker. That fix was correct for class/field obfuscation but did not address R8's separate **resource** shrinker. The comment at [ScaleImageHelper.kt:9](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/helper/ScaleImageHelper.kt#L9) — *"Uses Resources.getIdentifier() which is R8/ProGuard-safe"* — captured a partial truth and masked the remaining risk (see brainstorm: `docs/brainstorms/2026-04-20-ma-3778-scale-image-missing-brainstorm.md`).
- **Impact:** First-time scale/monitor users see a generic placeholder on Scale Setup, Scale Details, Scale Discovered popup, Scale Setup Loader, and the Scale list cards — the exact flows where the specific device artwork is most useful.

## Proposed Solution

Add a single `res/raw/keep.xml` file declaring wildcard `tools:keep` patterns that protect every drawable `ScaleImageHelper` may resolve at runtime. Refresh the misleading helper comment to distinguish code vs. resource shrinking.

### File 1 — new — `Android/app/src/main/res/raw/keep.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources
    xmlns:tools="http://schemas.android.com/tools"
    tools:keep="@drawable/scale_*,@drawable/monitor_*" />
```

This is the canonical Android mechanism for protecting resources that are only referenced via `Resources.getIdentifier()` or reflection. AGP processes any file in `res/raw/` that carries `tools:keep` — the filename `keep.xml` is convention, not a requirement. The comma-separated wildcard pattern covers every current scale/monitor drawable and any future asset following the same naming convention, so we don't need to maintain an explicit list.

### File 2 — edit — `Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/helper/ScaleImageHelper.kt`

Replace the KDoc comment at lines 7-10:

```kotlin
  /**
   * Returns the drawable resource for a given scale SKU, or placeholder image if not found.
   *
   * Lookup uses Resources.getIdentifier(), which survives R8 code shrinking but is invisible to
   * R8's resource shrinker. The scale_* and monitor_* drawables this resolves are kept in release
   * builds by res/raw/keep.xml — keep that file in sync if this naming convention ever changes.
   */
```

This is a single-block documentation change. It directly prevents the next engineer from drawing the same conclusion that led to this bug shipping.

## Technical Considerations

- **Architecture impact:** None. The fix lives entirely in resource configuration; no code paths, composables, DI graph, or navigation are affected.
- **Performance:** None on runtime. The protected drawables were always intended to ship — we're restoring the correct set.
- **APK size:** Release APK size increases by the sum of the stripped-but-needed drawables. Expect ~1–3 MB based on 23 scale PNGs (variable sizes) plus one monitor vector. This is the correct size — debug builds always shipped with these drawables. To be measured and recorded in the PR body (see Acceptance Criteria).
- **Security / privacy:** No change. Static assets only.
- **R8 compatibility:** `tools:keep` via `res/raw/keep.xml` is a long-standing AGP-stable feature, supported since AGP 3.x. No version pins required.
- **Wildcard semantics:** `tools:keep="@drawable/scale_*"` protects the logical resource across every density qualifier and night-mode variant — `drawable-hdpi`, `drawable-xhdpi`, `drawable-night`, etc. — because the shrinker operates on logical resource names.
- **Redundancy with `AppIcons`:** [AppIcons.kt:33, 169](Android/app/src/main/java/com/dmdbrands/gurus/weight/resources/AppIcons.kt) already references `scale_0412_weight_only` and `scale_0412_user_name` statically. Those two are also matched by the `scale_*` wildcard. Double-keeping a resource is harmless.

## System-Wide Impact

- **Interaction graph:** `AppScaleImage` (shared composable) → `ScaleUtility.scaleImageResource()` → `Resources.getIdentifier()` → `R.drawable.*`. No callbacks, no observers, no state. One call per screen frame, memoized at the Compose layer by `Int` resource id.
- **Error propagation:** Before fix, `getIdentifier()` returns `0` → helper returns `AppIcons.Default.ScalePlaceholder` silently. After fix, `getIdentifier()` returns the real resource id. Fallback path still covers genuinely-missing drawables (follow-up MA ticket proposed for missing `monitor_0603/0661/0634` assets).
- **State lifecycle risks:** None. Stateless resource lookup.
- **API surface parity:** Sole `Resources.getIdentifier()` caller in the entire Android tree is `ScaleImageHelper.kt:16` (repo research confirmed zero other instances). There is no parallel surface that needs the same fix.
- **Integration test scenarios:** This is a release-APK-only bug; unit tests and debug instrumentation tests cannot reproduce it. See Verification below for manual test steps.

## Acceptance Criteria

### Functional Requirements

- [ ] `Android/app/src/main/res/raw/keep.xml` exists and contains `tools:keep="@drawable/scale_*,@drawable/monitor_*"`.
- [ ] The misleading comment at [ScaleImageHelper.kt:7-10](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/helper/ScaleImageHelper.kt#L7-L10) is updated to clarify code vs. resource shrinking and reference `keep.xml`.
- [ ] A signed release APK built with the signing params in `MEMORY.md` installs on a physical device and renders the correct device image (not `ScalePlaceholder`) on all five consumer surfaces:
  - [AppScaleCard.kt](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/components/AppScaleCard.kt) — scale list cards
  - [ScaleDetailsScreen.kt](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/scaleDetails/screens/ScaleDetailsScreen.kt) — scale settings/details page
  - [ScaleInfo.kt](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/ScaleSetup/components/ScaleInfo.kt) — scale setup instructions
  - [ScaleSetupLoader.kt](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/ScaleSetup/components/ScaleSetupLoader.kt) — scale connection loader
  - [ScaleDiscoveredPopup.kt](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/components/ScaleDiscoveredPopup.kt) — discovery prompt
- [ ] Debug build behavior is unchanged (all scale/monitor images continue to render).
- [ ] `./gradlew assembleDebug` and `./gradlew detekt` succeed.
- [ ] `./gradlew test :app:jacocoTestReport :app:jacocoTestCoverageVerification` succeeds (coverage threshold unchanged).

### Non-Functional Requirements

- [ ] APK size delta (release, before vs. after) is measured and noted in the PR body. Expected ballpark: +1–3 MB.
- [ ] `aapt dump resources app-release.apk | grep -E "scale_|monitor_"` (or unzipping the APK and listing `res/drawable*/`) lists every `scale_*` / `monitor_*` drawable after the fix — zero before.

### Quality Gates

- [ ] Commit message format: `MA-3778 <Description>` (lefthook validates).
- [ ] PR title: `MA-3778 Fix scale drawables stripped from release APK by resource shrinker` (≤ 70 chars).
- [ ] PR body follows the observed `## Summary` bullet format.

## Verification (Release-Build-Only Bug — Critical)

CI covers only debug builds (see Android/CLAUDE.md "Build Commands"), so the fix **must** be manually verified on a signed release APK before merge.

**Build signed release APK** (per `~/.claude/.../memory/android-signing.md`):

```bash
cd Android && ./gradlew clean assembleRelease \
  -Pandroid.injected.signing.store.file=/Users/kaviyam/work/keys/weightgurus \
  -Pandroid.injected.signing.store.password=Design1st \
  -Pandroid.injected.signing.key.alias=weightgurus \
  -Pandroid.injected.signing.key.password=Design1st
```

**Install on connected device** (the same RZCT904CQAL already authorized for this session):

```bash
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
adb install -r "Android/app/build/outputs/apk/release/Weight gurus-release-v5.0.0(800000)-*.apk"
```

**Manual flows to exercise:**

1. Launch app → dashboard → verify scale card image (AppScaleCard).
2. Tap scale card → Scale Details screen → verify hero image (ScaleDetailsScreen).
3. Settings → Add new scale → scale list selection → verify per-SKU thumbnails (ScaleInfo).
4. Start scale discovery → observe loader with image (ScaleSetupLoader).
5. When scale is found → verify Scale Discovered popup image (ScaleDiscoveredPopup).
6. Confirm BPM monitor image renders on the Scale Details screen for SKU 0663 (the only monitor drawable currently in the repo).

Each of these was rendering as `ScalePlaceholder` before the fix per the user-observed bug.

**Optional static validation:**

```bash
# Confirm drawables are in the packaged APK
unzip -l Android/app/build/outputs/apk/release/*.apk | grep -E "scale_|monitor_" | wc -l
```

Expect a non-zero count after the fix (was ~2 before — only the statically-referenced variants).

## Success Metrics

- 100% of target device artwork renders (no placeholders) across all five screens in signed release builds.
- APK-size regression kept within the +1–3 MB budget (measured in PR body).
- No Firebase Crashlytics / detekt / CI regressions.

## Dependencies & Risks

- **Dependencies:** None. No new libraries, no version changes. Uses an AGP feature already available to the build.
- **Risk 1 — Over-keeping:** Wildcards could, in theory, protect a truly-dead `scale_*` drawable and prevent shrinker cleanup. Mitigation: acceptable; the naming namespace is tightly owned by device artwork, and the APK-size impact is bounded. Audits for truly unused drawables can be done via `./gradlew :app:analyzeReleaseBundle` if ever needed.
- **Risk 2 — Naming drift:** If a future asset uses a different prefix (e.g., `device_*`) it won't be protected. Mitigation: the refreshed KDoc at `ScaleImageHelper.kt:7-10` calls out that `keep.xml` must stay in sync with naming.
- **Risk 3 — Silent future regressions:** The helper returns `AppIcons.Default.ScalePlaceholder` silently when `getIdentifier()` returns `0`. Adding an `AppLog.w` at that branch would turn the next failure into a diagnosable log instead of a silent UI miss. Flagged as follow-up in the brainstorm's Open Questions; intentionally out of scope to keep this PR minimal.
- **Risk 4 — Missing BPM drawables (0603 / 0661 / 0634):** Orthogonal gap found during brainstorm research — those SKUs will still show placeholder because no drawable exists, not because the shrinker stripped one. Tracked as a separate follow-up.

## Implementation Steps

1. Create `Android/app/src/main/res/raw/keep.xml` with the content in "Proposed Solution → File 1".
2. Edit [ScaleImageHelper.kt:7-10](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/helper/ScaleImageHelper.kt#L7-L10) — replace the KDoc comment with the version in "Proposed Solution → File 2".
3. Run `./gradlew assembleDebug detekt test` from `Android/` — confirm green.
4. Build signed release APK with the command in Verification.
5. Install on connected device; walk through all five manual flows.
6. Record APK size delta.
7. Commit with message `MA-3778 Fix scale drawables stripped from release APK by resource shrinker`.
8. Open PR with `## Summary` body, test plan, and APK size delta.

## Out of Scope / Follow-ups

- Add `AppLog.w(TAG, "scale drawable missing for sku=$sku")` at the `resId == 0` branch in `ScaleImageHelper` — would have caught this bug in production logs; ship as a separate MA ticket.
- Add missing BPM monitor drawables for SKUs 0603, 0661, 0634 — separate MA ticket; product/design asset request.
- Add a brief R8 / resource-shrinking note to `Android/CLAUDE.md` so the next engineer searching "release APK", "resource shrinker", or "keep.xml" finds guidance without re-deriving it.
- Consider a lightweight CI lane that builds a signed release APK and runs a smoke UI test, to catch future release-only regressions before they ship.

## Sources & References

### Origin

- **Brainstorm:** [docs/brainstorms/2026-04-20-ma-3778-scale-image-missing-brainstorm.md](../brainstorms/2026-04-20-ma-3778-scale-image-missing-brainstorm.md) — carried forward: (1) root-cause hypothesis confirmed by static checks, (2) `keep.xml` wildcard approach chosen over static `R.drawable.*` list and over `Map<String, Int>` refactor, (3) scope constrained to the single-file fix with two follow-ups documented.

### Internal References

- [Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/helper/ScaleImageHelper.kt:6-23](Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/helper/ScaleImageHelper.kt#L6-L23) — lookup site to protect
- [Android/app/build.gradle.kts:65-81](Android/app/build.gradle.kts#L65-L81) — release build config with `isMinifyEnabled` / `isShrinkResources`
- [Android/app/src/main/java/com/dmdbrands/gurus/weight/resources/AppIcons.kt:33,169](Android/app/src/main/java/com/dmdbrands/gurus/weight/resources/AppIcons.kt) — the two `scale_*` drawables already protected via static refs
- [Android/app/proguard-rules.pro](Android/app/proguard-rules.pro) — existing code-shrinker rules (untouched by this fix)
- [Android/CLAUDE.md](Android/CLAUDE.md) — build commands and the commit/PR conventions to follow

### Related Work

- **MA-3018** (Mar 6, 2026) — R8/ProGuard class-level fixes that switched `ScaleImageHelper` to `Resources.getIdentifier()`. Made class lookup R8-safe but did not protect the resources themselves; this plan closes that gap.
- **MA-3484** (Apr 10, 2026) — Added monitor asset support with only `monitor_0663.xml`; uncovered the missing-drawable asset gap noted in Follow-ups.

### Ticket

- [MA-3778](https://greatergoods.atlassian.net/browse/MA-3778) — "Scale image is not displayed across the app"
