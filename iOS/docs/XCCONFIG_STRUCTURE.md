# XCConfig Structure Guide

This document explains how build configurations are organized in `meApp`.

## Active Environments

Only two environments are supported:

- `Dev`
- `Production`

## XCConfig Files

Location: `meApp/Core/Config/`

- `Dev.xcconfig`
  - `APP_ENV = DEV`
  - `API_BASE_URL = ec2-13-217-141-203.compute-1.amazonaws.com:3005/v3`
- `Production.xcconfig`
  - `APP_ENV = PRODUCTION`
  - `API_BASE_URL = api.weightgurus.com/v3`

## How Runtime Environment Is Resolved

File: `meApp/Core/Config/Environment.swift`

- Reads `APP_ENV` and `API_BASE_URL` from `Info.plist` values populated by the active xcconfig.
- Supports only:
  - `.dev`
  - `.production`
- URL scheme behavior:
  - `Dev` uses `http://`
  - `Production` uses `https://`

## Xcode Configuration Mapping

Project and targets (`meApp`, `meAppTests`, `meAppUITests`) should have only:

- `Dev` -> `Core/Config/Dev.xcconfig`
- `Production` -> `Core/Config/Production.xcconfig`

## Team Standard

- Local development: use `Dev`
- App Store / release builds: use `Production`
- Command-line builds should pass configuration explicitly in CI:

```bash
xcodebuild -project meApp.xcodeproj -scheme meApp -configuration Dev
xcodebuild -project meApp.xcodeproj -scheme meApp -configuration Production
```

## Default Configuration Guidance

If no `-configuration` is passed, Xcode/CLI uses the project default configuration.

Recommended team default:

- `Dev` for daily local work (safer, prevents accidental production builds).

If your repo currently uses `Production` as default, keep CI explicit with `-configuration Production` for release jobs.

### Change Default In Xcode UI

To change which configuration is used for command-line builds when `-configuration` is not provided:

1. Open `meApp.xcodeproj` in Xcode.
2. Select the project root `meApp` in the navigator.
3. Open the `Info` tab.
4. Under `Configurations`, find the project-level default (for command-line builds).
5. In the dropdown, switch between:
   - `Production` (current)
   - `Dev`

Use `Dev` for local development by default, and switch to `Production` when needed.

## Change Checklist

When adding/changing configuration behavior:

1. Update the xcconfig values in `meApp/Core/Config/`.
2. Ensure project + all targets map configs correctly in `meApp.xcodeproj/project.pbxproj`.
3. Keep `Environment.swift` enum and switch in sync with supported environments.
4. Verify scheme archive action uses `Production`.
