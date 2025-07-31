# Wifi Scale Setup Flow – Usage Guide

## Overview
The **WifiScaleSetup** module handles the end-to-end onboarding flow for *Wi-Fi-only* smart scales (e.g. SKUs **0384 / 0385**).  
It is implemented with pure **SwiftUI + Combine** and follows the Clean-Architecture layering used across *meApp*.

```
Features/ScaleSetup/Wifi/
├── Stores/                # Business logic & side-effects
│   └── WifiScaleSetupStore.swift
├── Views/                 # SwiftUI pages & components
├── Models/                # Enums / DTOs used by the flow
└── WifiScaleSetupFlow.md  # ← THIS FILE
```

---

## Entry Points
The wizard is launched manually from the *Add Scale* UI.  
`WifiScaleSetupScreen` receives **one** required parameter:

```swift
WifiScaleSetupScreen(sku: "0385")
```

Inside the screen a `@StateObject` store is created:

```swift
@StateObject private var setupStore = WifiScaleSetupStore()
```

The store drives all state while the view hierarchy re-renders reactively via `@Published` properties.

---

## Architecture
```
┌───────────────┐   intents   ┌────────────────────────┐
│ SwiftUI Views │ ──────────▶ │  WifiScaleSetupStore   │
└───────────────┘  @StateObj  │  (business logic)      │
        ▲                    └────────┬───────────────┘
        │ Combine publishers           │ async/await
        │                               ▼
        │                     ┌────────────────────────┐
        │     Wi-Fi / BLE     │  WifiScaleService      │
        └──────────────────── │  PermissionsService    │
                              │  NetworkMonitor        │
                              └────────────────────────┘
```
The store **never** touches UI; it emits state that SwiftUI consumes.

---

## Step-by-Step Wizard
`WifiScaleSetupStep` enumerates **14** distinct pages.  The *Permissions* page is automatically skipped when all requirements are already granted.

| Index | Step | Purpose |
|------:|------|---------|
| 0 | `intro` | Product intro & *Get MAC* shortcut |
| 1 | `permissions` | Requests Location + switches |
| 2 | `wifiPassword` | Choose SSID & password |
| 3 | `selectUser` | Pick user number (1-4) |
| 4 | `activatePairingMode` | Hold **UNIT** button – scale shows *SET UP 1* |
| 5 | `connectionConfirm` | User taps image that matches scale screen *(Complete vs AP)* |
| 6 | `apMode` | Prompt to switch phone Wi-Fi to `gg_SmartScale_##` |
| 7 | `apModeConfirm` | Wait for scale to count to 4 *(AP confirmation)* |
| 8 | `errorSelect` | User picks error code (t204…t325) |
| 9 | `errorDetail` | Troubleshooting text |
| 10 | `copyMacAddress` | Shows MAC & **COPY** button |
| 11 | `stepOn` | Ask user to step on scale |
| 12 | `setupFinish` | Success – dismisses on *Finish* |

> Navigation helpers `moveToNextStep()` / `moveToPreviousStep()` honour permission-skipping via `adjustedIndex()`.

---

## Variable Glossary (WifiScaleSetupStore)
| Property | Purpose |
|----------|---------|
| `@Published currentStepIndex` | Zero-based index into `steps` array.  Updates `currentStep`. |
| `steps` | Static `[WifiScaleSetupStep]` (allCases). |
| `isForGetMac` | **true** when user entered *Get MAC* flow via intro CTA. Changes navigation logic. |
| `permissionsSkipped` | Set when user taps **Skip** on permissions page. Forces *AP-mode-only* path. |
| `selectedConnectionMode` | `.complete`, `.apMode`, or `.none` chosen on *connectionConfirm*. |
| `networkForm` | Reactive form holding `ssid`, `password`, `networkHasNoPassword`. |
| `wifiStatus` | Cached `WifiStatus` from `WifiScaleService`. Used to prefill SSID / MAC. |
| `scaleToken` | Backend token fetched once via `getScaleToken(r:"4")` and cached. |
| `retrievedMacAddress` | Formatted BSSID produced by `getMacAddress()` coroutine. |
| `errorSelectSourceStep` | Remembers the page that led to error branch for proper *Back* handling. |
| `stepOnSourceStep` | Same idea for the *Step On* branch. |
| `isNextEnabled` | Computed each time inputs change to disable footer button. |

---

## Primary Flows
### 1. Normal Setup *(Complete mode)*
```
intro ─▶ permissions ─▶ wifiPassword ─▶ selectUser ─▶ activatePairingMode ─▶
connectionConfirm(complete) ─▶ stepOn ─▶ saveScale() ─▶ setupFinish
```
Logic snippets:
* User picks **Setup Complete** image → `selectedConnectionMode = .complete`.
* `handleNextButtonClick` on `connectionConfirm` jumps directly to `.stepOn`.
* `saveScale()` builds a `Device` object & persists via `ScaleService.createDevice` then shows finish page.

### 2. AP-Mode Setup
```
… activatePairingMode ─▶ connectionConfirm(apMode) ─▶ apMode ─▶ apModeConfirm ─▶ stepOn
```
* On *apMode* page the app asks user to switch Wi-Fi. `startApMode()` retries up to **5×** on failure.
* Successful confirmation flows to `stepOn` identical to normal path.

### 3. Permissions-Skipped Path
User taps **Skip** on *permissions* page →
```
permissionsSkipped = true
connectionConfirm.mode = .apModeOnly   // complete option hidden
selectedConnectionMode forced to .apMode
```
The wizard therefore follows the AP-Mode branch even though regular smart-connect cannot run.

### 4. *Get MAC Address* Flow
From *Intro* the *I see something else?* button sets `isForGetMac = true` & jumps:
```
intro ─▶ (permissions?) ─▶ activatePairingMode ─▶ connectionConfirm(apModeOnly) ─▶
apMode ─▶ getMacAddress()  ~30 s poll ─▶ copyMacAddress ─▶ setupFinish
```
* `getMacAddress()` polls `wifiStatus?.bssid` every second (timeout ≈ 29.5 s).  
* On success it normalises bytes (`0f:2a:…`) and sets `retrievedMacAddress`.
* The *COPY MAC ADDRESS* button uses `UIPasteboard` in `CopyMacAddressViewModel` (see view).

---

## Error Handling Branch
The *Error Select* → *Error Detail* sub-flow lets the user pick an on-scale `Err` code.
`WifiErrorCode.shouldUseNumberedMessages` decides bullet style.
After reading the detail the *Finish* button calls `exitSetup()` which simply `dismissAction()`.

---

## Helper Coroutines
| Function | Description |
|----------|-------------|
| `startSmartConnect()` | Performs standard smart-connect unless `permissionsSkipped` → early-return. |
| `startApMode(retryCount:)` | Stops any session then triggers AP config; 5 retries with 5 s back-off. |
| `getMacAddress()` | Loader + initial 3 s sleep → 1 s polling loop until BSSID found or timeout. |
| `fetchWifiScaleToken()` | One-shot network call; result cached in `scaleToken`. |

---

## Testing Checklist
- [ ] Run flow **with** permissions → should skip *Permissions* page on 2nd attempt.
- [ ] Tap **Skip** → AP-Mode UI only.
- [ ] *Get MAC* branch returns correctly-formatted MAC.
- [ ] Invalid password disables **Connect**.
- [ ] Leaving wizard via X ✕ shows *Exit* alert.
- [ ] Successful save posts `.scaleAddedOrUpdated` notification.

---

_Last updated: {{25 July 2025}}_ 