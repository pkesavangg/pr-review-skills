# Brainstorm: Wire ReadingToast in AppViewModel

**Date:** 2026-04-27
**Ticket:** MA-3822

## What We're Building

Wire the `ReadingToast` into AppViewModel's `saveEntry()` flow so BLE readings show the toast card instead of saving immediately. User decides via toast actions: SAVE/ASSIGN to persist, DISCARD/DON'T ASSIGN to drop.

## Flow

1. BLE reading arrives → `handleEntryResponse()` → `saveEntry(entries)`
2. `saveEntry()` builds the `ScaleEntry` list (BMI calc etc.) but does NOT call `entryService.addEntry()`
3. Detect reading type from device SKU/protocol:
   - Baby scale (SKU 0220/0222 or protocol A6) → `ProductType.BABY`
   - BPM (SKU 0603/0661/0634/0663) → `ProductType.BLOOD_PRESSURE`
   - Everything else → `ProductType.MY_WEIGHT`
4. Format reading string (e.g. "149.2 lbs", "14 lbs 6 oz", "120/80 mmhg pulse 65")
5. Show `dialogQueueService.showToast(Toast.Custom(ReadingToast(...)))` with:
   - `primaryAction` → save the entry (weight/BPM) or open AssignMeasurementDialog (baby)
   - `secondaryAction` → discard (do nothing)
6. For baby + ASSIGN → show `AssignMeasurementDialog` → on select → create `BabyEntry` + save

## Key Decisions

1. **Toast first, save on action** — entry is held in memory until user acts
2. **Detection by device** — use `DeviceHelper.isBabyScale(sku)` / `isBpmDevice(sku)` on the paired device
3. **Hold entries in a local var** — `saveEntry()` stores the prepared entries, toast callbacks close over them
4. **Skip during setup** — keep existing `isSetupInProgress` guard, save immediately during setup (no toast)
5. **Format reading** from the ScaleEntry data (weight + unit for weight, systolic/diastolic for BPM, lbs/oz for baby)
