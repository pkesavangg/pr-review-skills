# BtWifi Scale Setup Flow – Usage Guide

## Overview
The **BtWifiScaleSetup** module orchestrates the end-to-end onboarding flow for smart Wi-Fi (R4) scales (e.g. SKU *0412*).  
It’s implemented in pure SwiftUI + Combine and follows the same Clean-Architecture layering rules used throughout *meApp*.

## Entry Points
The wizard can be launched from **two** places:

1. **Scale Discovered** half-sheet (automatic)  
   - `BottomTabBarViewModel` listens to `BluetoothService.deviceDiscoveredPublisher`.  
   - When `shouldShowDiscoveredScale(for:)` returns *true*, it stores the `Device` + `DeviceDiscoveryEvent` and lifts them into `BottomTabBarView.discoveredScale`.
   - `BottomTabBarView` presents `ScaleDiscoveredSheetView` via `.sheet(item:)`.  On *Connect* tap it calls `openScaleSetup(...)` which sets `setupPayload` and immediately shows `BtWifiScaleSetupScreen` (or A6 depending on `setupType`).

2. **Manual navigation** (e.g. Settings → My Scales → *Add Scale*)  
   - Simply pushes `BtWifiScaleSetupScreen` with just the SKU.

Regardless of origin the screen receives:
```swift
BtWifiScaleSetupScreen(
    sku: "0412",
    discoveredScale: Device?,       // optional – non-nil when launched from sheet
    discoveryEvent: DeviceDiscoveryEvent? // same
)
```

Key classes / views:
- `BtWifiScaleSetupScreen` – top-level SwiftUI screen embedding the multi-step wizard
- `BtWifiScaleSetupStore` – @MainActor store powering the flow (state, side-effects)
- `DuplicateUserView`, `MaxUserListView` – error sub-screens rendered on the **Gathering Network** step
- `UserNameForm` + `Validator.swift` – reactive validation pipeline for the duplicate-user text-field

> All strings live in `BtWifiScaleSetupStrings.swift`; **never** hard-code text in views.

---

## Architecture
```
┌───────────────┐    user input    ┌──────────────────────────┐
│ SwiftUI Views │ ───────────────▶ │  BtWifiScaleSetupStore   │
└───────────────┘   @StateObject   │  (business logic)        │
        ▲                         │                          │
        │ Combine publishers      └────────┬─────────────────┘
        │                                   │ async/await
        │                                   ▼
        │         Bluetooth events   ┌──────────────────────────┐
        └─────────────────────────── │    BluetoothService      │
                                     └──────────────────────────┘
```
The store stays *unidirectional*: UI dispatches intents → store mutates `@Published` state → UI re-renders.

---

## Step-by-Step Wizard
`BtWifiScaleSetupStep` (`enum`) defines **15** distinct pages.  The wizard can dynamically skip *Permissions* when Bluetooth + Location are already granted.

| Index | Step | Purpose |
|------:|------|---------|
| 0 | `intro` | Scale marketing, model highlights |
| 1 | `permissions` | Requests Bluetooth & Bluetooth-switch permissions (auto-triggers prompt) |
| 2 | `wakeup` | Instructs user to tap scale; kicks off *pair* scan & 30 s timeout |
| 3 | `connectingBluetooth` | Shows `BluetoothConnectionView` with spinner / success / failure states |
| 4 | `gatheringNetwork` | Retrieves Wi-Fi list **or** renders **error sub-flow** (duplicate / max user) |
| 5 | `availableWifiList` | SSID picker |
| 6 | `wifiPassword` | Password entry |
| 7 | `connectingWifi` | Progress bar while scale joins network |
| 8-10 | `customizeSettings / viewSettings / updateSettings` | Surface R4 settings (units, metrics, etc.) |
| 11 | `stepOn` | Prompt to step on scale |
| 12 | `measurement` | Live impedance & weight graph |
| 13 | `scaleConnected` | Success / CTA to dashboard |

Navigation helpers:
```swift
moveToNextStep()      // advances, honours permission-skipping
moveToPreviousStep()  // goes back (disabled on intro & duplicate-error)
```

---

## Error Branches (Step 4)
During `confirmSmartPair` the SDK can return three sentinel cases:

1. `.creationCompleted` – happy path → continue wizard
2. `.duplicateUserError` → `scaleSetupError = .duplicatesFound`
3. `.memoryFull` → `scaleSetupError = .maxUserReached`

The store reacts in `confirmPair()` and sets `currentStep = .gatheringNetwork` so the **same page** can either:
- Show `ConnectionPromptView` *(no error)*
- Render `DuplicateUserView` *(error = duplicatesFound)*
- Render `MaxUserListView` *(error = maxUserReached)*

### Duplicate User Flow (`DuplicateUserView`)
- `UserNameForm` drives the *Choose a new name* text-field
- **Restore account** button triggers `handleRestoreAccount()` ➜ confirmation alert ➜ deletes duplicate users via `deleteUsers()` then restarts pairing.
- Footer **Save** button is enabled only when `displayName` passes **all** validators (see below).

### Max-User Flow (`MaxUserListView`)
- Lists current users (`DeviceUser[]`) in an embedded `DeviceUserListView`
- Tapping delete opens confirmation alert → `deleteUserFromScale()` → retries pairing

---

## Username Validation Pipeline
`UserNameForm` extends the generic reactive-form system.

Active validators on `displayName`:
1. `.required` – cannot be empty
2. `.noWhiteSpace` – rejects whitespace-only
3. `.namePattern` – `^[A-Za-z0-9 _]*[A-Za-z0-9][A-Za-z0-9 _]*$`
4. `.userNameUnavailable` – *guest* is reserved
5. `.duplicate` – dynamic – fails if value conflicts with `userList` fetched from scale

`Validator.duplicateUser { self.userList.map(\.$name) }` is re-registered every time the user list changes.

Error priority is fixed – first match wins; messages live in `FormErrorMessages`.

---

## Exit Alert Logic
Calling the *X* button invokes `handleExit()`:
- If wizard already finished (`scaleConnected`) simply `dismissAction()`.
- Otherwise shows **ExitBtWifiSetupAlert** (strings in `AlertStrings.ExitBtWifiSetupAlert`).
  - *Exit* – disconnects unsaved scale (`disconnectDevice`) + cancels Wi-Fi (`cancelWifi`) then dismisses.
  - *Go Back* – closes alert, wizard stays.

---

## Key Combine Streams
```swift
permissionsService.$permissions     // live permission changes
networkMonitor.$isConnected          // network reachability
userNameForm.formDidChange           // debounced text-field edits
deviceDiscoveryPublisher             // BluetoothService scan events
```
These pipelines automatically toggle `isNextEnabled` and drive step transitions, ensuring a fully reactive UI.

---

## Testing Checklist
- [ ] Launch flow with **no permissions** → should auto-prompt & skip *Permissions* on second run
- [ ] Simulate `.duplicateUserError` → duplicate screen shows, Save disabled until unique name
- [ ] Simulate `.memoryFull` → list screen shows, deleting one user lets flow continue
- [ ] Tap *Exit* mid-flow → scale disconnects, modal dismissed
- [ ] Complete happy path → scale saved, Wi-Fi list fetched

---

## Troubleshooting
| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| *Bluetooth spinner never stops* | `ConnectionState` stuck on `.loading` | Check `confirmSmartPair()` error branch |
| *Next button disabled on Permissions* | BT switch off or no network | Toggle Bluetooth & ensure internet |
| *Duplicate user keeps failing* | Still conflicts | Ensure `userList` refreshed; name passes `namePattern` |

---

_Last updated: {{14 July 2025}}_ 