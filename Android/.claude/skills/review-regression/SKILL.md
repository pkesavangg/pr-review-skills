---
name: review-regression
description: Check whether a meApp Android change risks breaking behaviour outside its stated scope — public API/signature changes, stale tests, Hilt DI gaps, Room migrations, and build-config parity. Use when reviewing for regressions, or when the user says "will this break anything". Also called by /self-review and /review-pr.
---

Assess regression risk of the diff. Read-only.

## Instructions

1. **Public surface** — changed function/param/return signatures: find all callers (`rg`) and confirm they're updated.
2. **Hilt DI** — new/changed bindings: is every consumer bound? (a missing binding fails `assembleDebug`). Run agent `hilt-impact-finder` if unsure.
3. **Room** — schema change without a `@Database` version bump + `Migration` = runtime crash on upgrade. Flag it.
4. **Tests** — did behaviour change without updating the corresponding tests? Any test asserting the old behaviour now stale?
5. **Reducers/State** — new `Intent` or `State` field unhandled in the reducer or not `.copy()`-propagated.
6. **Build config** — `libs.versions.toml`/gradle changes: confirm both debug and release still assemble.
7. Report risk per area; overall **Low / Medium / High**. Name the concrete break scenario for any Medium/High.
