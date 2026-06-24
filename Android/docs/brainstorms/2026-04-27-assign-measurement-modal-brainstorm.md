# Brainstorm: Assign Measurement Modal

**Date:** 2026-04-27
**Ticket:** MA-3822

## What We're Building

A center dialog that opens when the user taps ASSIGN on the ReadingArrivalCard toast. It lets the user pick which baby a baby-scale reading belongs to.

**Figma:** node 29406:20060 in file k0HO1SquDGrYOcoMSbrzA0

**UI elements:**
- Baby avatar icon (centered, top)
- Title: "Assign Measurement"
- Subtitle: "Which baby is this measurement for?"
- Reading value in accent color (e.g. "179.2 lbs · Just now") — uses `rememberMeasurementText`
- Radio list of babies: `AppProfileAvatar` initial circle + name + age + radio button
- Primary: ASSIGN (filled button)
- Secondary: DON'T ASSIGN (text button)
- X close button (top-right)

**Edge cases:**
- 1 baby → auto-assign, skip modal
- 0 babies → show alert with "Add Baby" CTA
- ≥2 babies → show this modal

## Why This Approach

**Standalone `AssignMeasurementDialog` composable** — purpose-built, not stretching existing patterns.

- `AppRadioGroupModal` is too generic (no header icon, no measurement display, no avatar rows)
- `DialogModel.Custom` requires type-unsafe `Map<String, Any?>` casting
- A standalone Dialog() composable is the cleanest — compose it directly from the ReadingArrivalCard's primaryAction callback

## Key Decisions

1. **Standalone composable** using `Dialog()` — not DialogModel.Custom, not extending AppRadioGroupModal
2. **Baby list from `BabyProfileService.observeAll()`** — reactive, already used in MyKidsScreen
3. **Reuse `AppProfileAvatar`** for baby initial circles
4. **Reuse `rememberMeasurementText`** for the reading value display
5. **Age calculation** from `BabyProfile.birthdate` — compute "X months old" at display time
6. **State management** — local compose state (selectedBabyId) inside the dialog, no ViewModel needed
7. **Trigger** — ReadingArrivalCard's `primaryAction` callback shows the dialog via a state flag
