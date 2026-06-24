# Brainstorm: MA-3778 — Scale image not displayed across the app

**Date:** 2026-04-20
**Jira:** [MA-3778](https://greatergoods.atlassian.net/browse/MA-3778)
**Type:** Bug fix

## What We're Building

Restore scale/monitor device imagery across every screen of the Android release APK. The images render correctly in debug builds but disappear in signed release builds for every device SKU. We will protect the dynamically-referenced drawables from the R8 resource shrinker by adding a `res/raw/keep.xml` with wildcard entries for `@drawable/scale_*` and `@drawable/monitor_*`.

**In scope**
- Add `res/raw/keep.xml` with wildcard keep rules for scale/monitor drawables
- Validate a signed release APK shows images on affected screens (AppScaleCard, ScaleDetailsScreen, ScaleInfo, ScaleSetupLoader, ScaleDiscoveredPopup)

**Out of scope** (captured here, tracked separately if needed)
- Missing BPM monitor drawables for SKUs 0603 / 0661 / 0634 (only 0663 exists)
- Adding `AppLog` diagnostics to `ScaleImageHelper` when `getIdentifier()` returns 0

## Why This Approach

`ScaleImageHelper` resolves drawables by name with `Resources.getIdentifier("scale_{sku}", "drawable", packageName)`. Only two drawables — `scale_0412_weight_only`, `scale_0412_user_name` — are referenced statically via `R.drawable.*`. The other 21+ `scale_*` files and `monitor_0663.xml` are referenced exclusively at runtime, which R8's resource shrinker cannot detect.

The app's release build has both `isMinifyEnabled = true` and `isShrinkResources = true` (`app/build.gradle.kts:66-67`) and there is no `keep.xml` or `tools:keep` declaration anywhere under `res/`. As a result every dynamically-looked-up drawable is stripped from the APK and `Resources.getIdentifier()` returns 0, so the helper silently falls back to `AppIcons.Default.ScalePlaceholder` — hence "no scale images across the app" in release.

`keep.xml` is the canonical Android mechanism for this problem. A single file with two wildcard patterns protects every current scale/monitor drawable and automatically covers any future asset following the same naming convention, with no code changes. It is the smallest safe fix.

## Key Decisions

- **Use `res/raw/keep.xml` with `tools:keep` wildcards** instead of adding static `R.drawable.*` references or replacing `getIdentifier()` with a hand-written SKU→drawable map. Wildcards remove the ongoing maintenance burden of keeping a list in sync with `res/drawable*/`.
- **Keep the existing `Resources.getIdentifier()` lookup** in `ScaleImageHelper`. It is already R8-class-keep safe (per MA-3018); the only missing piece is resource-level protection.
- **Ship this as a focused one-file change.** Asset gaps for missing BPM monitor SKUs and the absence of failure logging are real follow-ups but are not regressions caused by this ticket and should be filed separately to keep the fix reviewable and the PR scope tight.

## Resolved Questions

- **Which devices are affected?** All devices across the app — confirmed by the user. This ruled out the "incomplete BPM asset migration" hypothesis as the primary cause and pointed to resource shrinker stripping.
- **Is `keep.xml` the right tool?** Yes — no existing keep file in `res/raw/`, and the `Resources.getIdentifier()` pattern used by `ScaleImageHelper` is exactly what `keep.xml` is designed to protect.

## Open Questions

None blocking. Follow-ups to consider filing as separate tickets:
- Add missing BPM monitor drawables for SKUs 0603, 0661, 0634 (currently silently show placeholder).
- Add `AppLog.w` entry in `ScaleImageHelper` when `getIdentifier()` returns 0 so future regressions are visible in logs instead of failing silently.
