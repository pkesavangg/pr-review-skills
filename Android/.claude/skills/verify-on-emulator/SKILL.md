---
name: verify-on-emulator
description: Verify a UI-affecting change on an Android emulator/device end-to-end — install against the prod API, drive the flow, and record a video — before pushing. Use for any change that alters UI or user-facing behaviour, or when the user says "run it on the emulator", "record a video", "verify on device".
---

Prove a change works on a real emulator/device with a recorded video. This is the pre-push bar for UI-affecting Android changes (iOS cannot automate this; Android can).

The change to verify is: $ARGUMENTS

## Instructions

### 1 — Point the build at the prod API (local only)
- Swap the debug `BASE_URL` to `https://api.weightgurus.com/v3/` so the emulator talks to prod.
- **This edit is local only — never commit it.** Revert before `/commit`.

### 2 — Build, install, launch
```bash
adb devices                                  # confirm an emulator/device is attached
cd Android && ./gradlew installDebug
adb shell monkey -p com.dmdbrands.gurus.weight -c android.intent.category.LAUNCHER 1
```
Use the `android-cli` skill for AVD management, screenshots, and UI inspection if needed.

### 3 — Drive the flow & record
```bash
adb shell screenrecord /sdcard/mob-xxxx.mp4   # Ctrl-C to stop
adb pull /sdcard/mob-xxxx.mp4 ./
```
- Log in with the QA test account (team-shared prod QA credentials — do not hardcode them anywhere).
- Exercise the exact acceptance-criteria path; capture the before/after behaviour.

### 4 — Report & attach
- Attach the recorded video to the PR (no video = not pushed).
- Revert the local `BASE_URL` change. Confirm `git status` shows no stray config edit before committing.
