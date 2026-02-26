# BtWifi Scale Setup Flow – Usage Guide

## Overview
The **BtWifiScaleSetup** module orchestrates the end-to-end onboarding flow for smart Wi-Fi (R4) scales (e.g. SKU *0412*).  
The store is split into a **main file** plus **extension files** by responsibility. All extensions extend `BtWifiScaleSetupStore`; the main file holds state, dependencies, lifecycle, and step view mapping.

## Entry Points
The wizard can be launched from **three** distinct contexts:

1. **Scale *Discovered* half-sheet** → `BottomTabBarView` → `ScaleDiscoveredSheetView` → `BtWifiScaleSetupScreen`
2. **Add Scale → enter SKU** → Settings → My Scales → Add → `BtWifiScaleSetupScreen(sku:)`
3. **Wi-Fi re-configuration** → Settings → My Scales → Scale Settings → Wi-Fi → `BtWifiScaleSetupScreen(sku:, savedScale:, …)` with `isWifiSetupOnly = true`

Key types:
- `BtWifiScaleSetupScreen` – top-level SwiftUI screen
- `BtWifiScaleSetupStore` – @MainActor store (main + extensions)
- `DuplicateUserView`, `MaxUserListView`, `UserNameForm`, `Validator` – error/validation UI

---

## Store File Layout

```
Stores/
├── BtWifiScaleSetupStore.swift              # Main: state, deps, init, stepViews
├── BtWifiScaleSetupStoreNavigationFlow.swift # configure, moveToNext/Previous, exit, footer/back
├── BtWifiScaleSetupStorePairingFlow.swift   # handleStepChange, pair, confirmPair, saveScale, discovery
├── BtWifiScaleSetupStoreWifiUserFlow.swift  # fetchWifiNetworks, setupWifi, network form
├── BtWifiScaleSetupStoreUserFlow.swift      # duplicate/max-user: restore, delete, getUserList
├── BtWifiScaleSetupStoreCustomization.swift # customize settings, view settings, dashboard, cleanup
├── BtWifiScaleSetupStoreActionHandlers.swift# UI actions: back, exit, try again, alerts
└── BtWifiScaleSetupStoreHelpers.swift       # permissions, timeouts, validation, navigateToStep
```

---

## File Responsibilities

### `BtWifiScaleSetupStore.swift` (main)
- **Dependencies**: `@Injector` services (notification, permissions, bluetooth, account, wifiScale, scale, push, entry, goalAlert), `networkMonitor`, `bluetoothSetupManager`, `wifiSetupManager`, `setupCoordinator`, `setupValidationService`
- **State**: `scaleItem`, `dismissAction`, `discoveredScale`, `discoveryEvent`, `scaleToken`, `firstName`, `isWifiSetupOnly`, `isReconnect`, `isDuplicated`, cancellables/tasks, all `@Published` (steps, connectionState, errors, forms, wifi, customize, etc.)
- **Lifecycle**: `init` – subscriptions to `permissionsService.$permissions`, `networkMonitor.$isConnected`, `userNameForm.formDidChange`, `subscribeToNetworkForm()`
- **Step views**: `stepViews` – maps `BtWifiScaleSetupStep` to SwiftUI views (intro, permissions, wakeup, connectingBluetooth, gatheringNetwork, availableWifiList, wifiPassword, connectingWifi, customizeSettings, viewSettings, updateSettings, stepOn, measurement, scaleConnected)

### `BtWifiScaleSetupStoreNavigationFlow.swift`
- **Navigation**: `nextButtonText`, `moveToNextStep()`, `moveToPreviousStep()`
- **Configuration**: `configure(with:discoveredScale:discoveryEvent:saveScale:isReconnect:isDuplicated:isWifiSetupOnly)` – SKU resolution, starting step, reset state
- **Exit / UI**: `performExitCleanup()`, `confirmExit()`, `showHelpModal()`, `showBluetoothTurnedOffAlert()`
- **Footer**: `shouldShowFooter()`, `shouldDisableBackButton()`

### `BtWifiScaleSetupStorePairingFlow.swift`
- **Step-driven logic**: `handleStepChange()` – per-step behaviour (wakeup→pair, connectingBluetooth→confirmPair, gatheringNetwork→fetchWifiNetworks, connectingWifi→setupWifi, updateSettings→updateCustomizeSettings, stepOn→live measurement + timeout, measurement→newEntry subscription + timeout, etc.)
- **Connection**: `setConnectionState(_:allowNetworkErrors:)`, `handlePermissionChange()`, `resetDiscoveryState()`
- **Pairing**: `confirmPair()`, `saveScale()`, `fetchWifiScaleToken()`, `pair()`, `handleDeviceDiscovery(_:)`, `showKnownScaleAlert()`

### `BtWifiScaleSetupStoreWifiUserFlow.swift`
- **Wi-Fi**: `fetchWifiNetworks(for:)`, `setupWifi()`, `checkDeviceInfoAfterWifiSetup(scale:)`
- **Form**: `subscribeToNetworkForm()`, `resetNetworkForm()`, `cancelWifi()`

### `BtWifiScaleSetupStoreUserFlow.swift`
- **Duplicate / max-user**: `getAccountNameForRestore()`, `findMatchingUserOnScale(scale:accountName:)`, `deleteMatchingUserFromScale(scale:user:)`, `resolveUsernameToPreserve(from:)`
- **Actions**: `performRestoreAccount()`, `restartConnectionAndNavigate()`, `handleSaveDuplicateUser()`, `deleteUserFromScale(_:)`, `restartConnection()`, `getUserList()`, `checkDuplicateUserList()`, `deleteUsers()`

### `BtWifiScaleSetupStoreCustomization.swift`
- **Customize UI**: `setCustomizationPage(_)`, `preloadDataForCustomizationPage`, `handleScaleModeChange(_:heartRateEnabled:)`, `showAccuCheckInfoModal()`, `addSelectedCustomizeItem`, `isCustomizeItemSelected`
- **View settings**: `performViewSettingsSave()`, `performViewSettingsBack()`, `setupScaleUsernameForm()`, `preloadScaleMode`, `preloadScaleMetrics`
- **Update & cleanup**: `updateCustomizeSettings()`, `checkGoalModalAfterSetup()`, `cleanup()`, `resumeScanningAndSyncDevices()`, `disconnectDevice()`
- **Snapshots**: `snapshotDashboardState()`, `hasDashboardCustomizationChanged()`, `discardDashboardCustomization()`
- **Dashboard / BIA**: `openBIAModel()`, `upgradeDashboardTypeFrom4To12WithDefaults()`, `isDashboardTypeFour`, `setupDashboardMetricsCustomization()`, `setupDashboardMetricsSubscriptions()`, `setupDashboardMetricsSync()`, `persistDashboardMetricsIfNeeded()`, `persistDashboardMetrics()`

### `BtWifiScaleSetupStoreActionHandlers.swift`
- **Navigation actions**: `handleBackButtonClick()`, `handleViewSettingsAction()`, `handleViewSettingsBack()`
- **Error flows**: `handleRestoreAccount()`, `handleDeleteUser(_:)`, `handleSkipWifiStep()`
- **Wi-Fi**: `handleWifiPasswordConnect()`, `handleNetworkSelection(_:)`
- **Exit**: `handleExit()`, `presentExitAlert(onConfirm:onCancel:)`, `handleSettingsWifiSetupExit()`
- **Next / retry**: `handleNextButtonClick()`, `tryAgainButtonHandler(isFromBtConnection:)`

### `BtWifiScaleSetupStoreHelpers.swift`
- **Permissions**: `bluetoothPermissionStates()`, `requestNextMissingBluetoothPermission(isBluetoothEnabled:isBluetoothSwitchEnabled:)`, `arePermissionsEnabled()`, `hasAllBtPermissions()`
- **Forms**: `resetFormState()`
- **Timeouts**: `startNetworkScanTimeout()`, `cancelNetworkScanTimeout()`, `cancelMeasurementSubscription()`, `cancelStepOnTimeout()`
- **Navigation**: `navigateToStep(_:delay:)`, `adjustedIndex(from:direction:)`
- **Validation**: `updateNextEnabled()` (uses `SetupValidationService` + `SetupValidationContext`)
- **Utils**: `isSameNetwork(_:_:)`

---

## Supporting Types

- **Setup**: `BluetoothSetupManager`, `WifiSetupManager`, `ScaleSetupCoordinator`, `SetupValidationService` (in `Setup/`)
- **Steps**: `BtWifiScaleSetupStep` (enum, all cases)
- **Errors**: `BtWifiScaleSetupError`
- **Strings**: `BtWifiScaleSetupStrings`, `ScaleSetupStrings`, `AlertStrings`, etc.

---

## Architecture (unchanged)

```
┌───────────────┐    user input    ┌──────────────────────────┐
│ SwiftUI Views │ ───────────────▶ │  BtWifiScaleSetupStore   │
└───────────────┘   @StateObject   │  (main + extensions)      │
        ▲                         └────────┬─────────────────┘
        │ Combine publishers                │ async/await
        │                                   ▼
        │         Bluetooth events   ┌──────────────────────────┐
        └─────────────────────────── │    BluetoothService      │
                                     └──────────────────────────┘
```

---

## Step-by-Step Wizard
Step behaviour is driven by `handleStepChange()` in **PairingFlow**; view mapping is in main store `stepViews`.

| Index | Step | Driven by (PairingFlow) |
|------:|------|--------------------------|
| 0 | intro | — |
| 1 | permissions | — |
| 2 | wakeup | `pair()` |
| 3 | connectingBluetooth | `confirmPair()` |
| 4 | gatheringNetwork | `fetchWifiNetworks(for:)` (WifiUserFlow) |
| 5 | availableWifiList | — |
| 6 | wifiPassword | — |
| 7 | connectingWifi | `setupWifi()` (WifiUserFlow) |
| 8–10 | customizeSettings / viewSettings / updateSettings | Customization + `updateCustomizeSettings()` |
| 11 | stepOn | live measurement + stepOn timeout |
| 12 | measurement | newEntry subscription + measurement timeout |
| 13 | scaleConnected | — |

Navigation: `moveToNextStep()` / `moveToPreviousStep()` in **NavigationFlow**; permission-skip via `setupCoordinator.adjustedIndex(...)` and `arePermissionsEnabled()` from **Helpers**.

---

## Error Branches (Step 4 – gatheringNetwork)
- `.creationCompleted` → continue
- `.duplicateUserError` → `scaleSetupError = .duplicatesFound` → **UserFlow** (restore/save duplicate), **ActionHandlers** (alerts)
- `.memoryFull` → `scaleSetupError = .maxUserReached` → **UserFlow** (getUserList, deleteUserFromScale), **ActionHandlers** (handleDeleteUser alert)

Duplicate user: `UserNameForm` + validators; restore via `handleRestoreAccount` → `performRestoreAccount`; Save via `handleSaveDuplicateUser`.  
Max user: `MaxUserListView`; delete via `handleDeleteUser` → `deleteUserFromScale` then restart pairing.

---

## Exit Alert Logic
**ActionHandlers**: `handleExit()` → if `scaleConnected` then `dismissAction()`; else **ExitBtWifiSetupAlert** (Exit → `performExitCleanup()` in NavigationFlow → disconnect, cancelWifi, dismiss; Go Back → close alert).

---

## Key Combine Streams
- `permissionsService.$permissions` → `updateNextEnabled`, `handlePermissionChange` (init in main)
- `networkMonitor.$isConnected` → same (init in main)
- `userNameForm.formDidChange` → `updateNextEnabled` (init in main)
- `subscribeToNetworkForm()` (WifiUserFlow) → form-driven validation
- Bluetooth discovery / newEntry / liveMeasurement – subscribed in **PairingFlow** inside `handleStepChange()` for relevant steps.

---

## Testing Checklist
- [ ] No permissions → auto-prompt; skip Permissions when already granted
- [ ] `.duplicateUserError` → duplicate screen; Save disabled until unique name
- [ ] `.memoryFull` → max user list; delete one → continue
- [ ] Exit mid-flow → disconnect + dismiss
- [ ] Happy path → scale saved, Wi-Fi list fetched

---

## Troubleshooting
| Symptom | Likely location | Fix |
|---------|-----------------|-----|
| Bluetooth spinner never stops | PairingFlow `confirmPair()` | Check error branches, `setConnectionState` |
| Next disabled on Permissions | Helpers `updateNextEnabled`, `arePermissionsEnabled` | BT switch + network |
| Duplicate user keeps failing | UserFlow `getUserList`, `checkDuplicateUserList`; validators | Refresh userList, namePattern |

---

_Last updated: 26 Feb 2026_ 
