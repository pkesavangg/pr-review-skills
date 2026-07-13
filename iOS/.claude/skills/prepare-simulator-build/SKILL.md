---
name: prepare-simulator-build
description: Switch the two device-only SPM packages (ggWifiScalePackage, ggBluetoothNativeLibrary) to their `simulator-support` branch so the iOS app builds and runs on the Simulator — for checking layout across screen sizes. Use when Kesavan says "prepare a simulator build", "make it build on the simulator", "switch packages to simulator", "run this on the simulator", "check the UI on different screen sizes", or the reverse — "restore device packages", "revert the simulator packages", "put the packages back". Runs `scripts/sim-packages.sh`.
---

Prepare the iOS project to build on the **Simulator** by pointing the two device-only
Swift packages at their `simulator-support` branch — and cleanly reverse it afterwards.

## Why this is needed

`ggWifiScalePackage` and `ggBluetoothNativeLibrary` link real Wi-Fi / BLE radio code on
their normal branches (`main` and `dev`), which does not compile/link for the iOS
Simulator. Each package keeps a **`simulator-support`** branch with stubbed radios so the
app builds and runs on any Simulator — the way to verify layout across screen sizes.

The branch is recorded in **two** files, so it must never be hand-edited by grep/sed
(three other packages also use `branch = main`). The `scripts/sim-packages.sh` helper
edits both surgically:

- `iOS/meApp.xcodeproj/project.pbxproj` — the package `requirement`
- `iOS/meApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` — branch + pinned revision

## This is a LOCAL, BUILD-ONLY change — never commit it

The `simulator-support` branches are a Simulator convenience only. **The two project files
must be restored to their device branches (`main` / `dev`) before committing or opening a
PR.** The script backs up the exact device-branch state and restores it byte-for-byte, so a
clean revert leaves no diff.

## Instructions

### To prepare a simulator build (the default request)

1. Run the switch from the repo root:
   ```bash
   ./scripts/sim-packages.sh simulator
   ```
   (Needs network — it reads the current `simulator-support` tip from GitHub. Add
   `--resolve` to also run `xcodebuild -resolvePackageDependencies` up front; otherwise
   Xcode resolves on the next build.)

2. Confirm to Kesavan what changed, then offer to build/run on a Simulator:
   ```bash
   ./scripts/run.sh ios dev      # pick a simulator from the list
   ```
   In Xcode, if it's open: **File ▸ Packages ▸ Reset Package Caches** (or just build) so it
   picks up the on-disk branch change.

3. **Always remind him** the two project files are now on `simulator-support` and must be
   restored before committing.

### To revert (back to device builds / before committing)

```bash
./scripts/sim-packages.sh restore
```
This restores `project.pbxproj` + `Package.resolved` to the exact pre-switch state
(`ggWifiScalePackage`→`main`, `ggBluetoothNativeLibrary`→`dev`) and removes the backup.

### To check current state

```bash
./scripts/sim-packages.sh status
```
Reports each package's current branch and whether the project is in `SIMULATOR` or
`DEVICE` mode.

## Guardrails

- If `status` already shows `SIMULATOR` mode, don't re-run `simulator` blindly — tell him
  it's already prepared.
- Before any `commit` / `raise-pr`, if the project is in `SIMULATOR` mode, **stop and
  offer to `restore` first** — these branches must not ship.
- If the script reports the branch isn't found or you're offline, don't hand-edit the
  files — surface the error; the switch needs GitHub access to pin the branch tip.

## Notes

- iOS-only. The Android build is unaffected.
- The backup lives at `<git-dir>/sim-packages-backup/` (inside `.git/`), so it never shows
  in `git status` and is never committed.
