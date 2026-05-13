# Privacy Compliance — App Store / Play Store

Rules catching additions that would break store submission or violate disclosed privacy practices. iOS-specific rules apply when iOS is detected; Android-specific rules apply when Android is detected.

---

## P1 — iOS 17+ required-reason API used without a `PrivacyInfo.xcprivacy` declaration

Apple requires apps using certain commonly-abused APIs to declare an approved reason code in a `PrivacyInfo.xcprivacy` manifest. Apps without the manifest, or apps using an API with no declared reason, get rejected at submission.

Current required-reason API categories (as of iOS 17.4):

| Category | Affected APIs | Manifest enum |
|---|---|---|
| File timestamp | `creationDate`, `modificationDate`, `attributeModificationDate`, `contentModificationDate`, `URLResourceValues` timestamp keys, `getattrlist`, `getattrlistbulk`, `fgetattrlist`, `stat`, `fstat`, `lstat` | `NSPrivacyAccessedAPICategoryFileTimestamp` |
| System boot time | `systemUptime`, `mach_absolute_time`, `mach_approximate_time` | `NSPrivacyAccessedAPICategorySystemBootTime` |
| Disk space | `volumeAvailableCapacityKey`, `volumeAvailableCapacityForImportantUsageKey`, `volumeAvailableCapacityForOpportunisticUsageKey`, `volumeTotalCapacityKey`, `systemFreeSize`, `systemSize`, `statfs`, `statvfs` | `NSPrivacyAccessedAPICategoryDiskSpace` |
| Active keyboards | `UITextInputMode.activeInputModes` | `NSPrivacyAccessedAPICategoryActiveKeyboards` |
| User defaults | `UserDefaults` | `NSPrivacyAccessedAPICategoryUserDefaults` |

**Sniff.** New `+` line usage of any API in the table. For each finding:

1. Check whether `PrivacyInfo.xcprivacy` is in `CHANGED_FILES` or the worktree.
2. If not present, OR present but doesn't list the relevant category in `NSPrivacyAccessedAPITypes` → P1.

**Fix.** Add or extend the manifest:
```xml
<key>NSPrivacyAccessedAPITypes</key>
<array>
    <dict>
        <key>NSPrivacyAccessedAPIType</key>
        <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
        <key>NSPrivacyAccessedAPITypeReasons</key>
        <array>
            <string>CA92.1</string>   <!-- Access info from same app group -->
        </array>
    </dict>
</array>
```

Approved reason codes are listed in [Apple's documentation](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files/describing_use_of_required_reason_api). Pick the closest match — submitting with no reason or a wrong reason gets rejected.

---

## P1 — Missing `NSXxxUsageDescription` for a new capability

When entitlements expand or APIs requiring user permission are added, the corresponding usage-description string must exist in `Info.plist`. Without it the OS doesn't display the permission prompt — the request silently fails, or on some iOS versions, the app crashes when the API is called.

| Capability / API | Required `Info.plist` key |
|---|---|
| `AVCaptureDevice`, `UIImagePickerController` (camera) | `NSCameraUsageDescription` |
| `PHPhotoLibrary` (read) | `NSPhotoLibraryUsageDescription` |
| `PHPhotoLibrary` (add) | `NSPhotoLibraryAddUsageDescription` |
| `CLLocationManager` (when in use) | `NSLocationWhenInUseUsageDescription` |
| `CLLocationManager` (always) | `NSLocationAlwaysAndWhenInUseUsageDescription` + `NSLocationWhenInUseUsageDescription` |
| `AVAudioSession`, `AVAudioRecorder` (microphone) | `NSMicrophoneUsageDescription` |
| `CNContactStore` | `NSContactsUsageDescription` |
| `EKEventStore` (calendar) | `NSCalendarsUsageDescription` |
| `EKEventStore` (reminders) | `NSRemindersUsageDescription` |
| `CBCentralManager` (Bluetooth, iOS 13+) | `NSBluetoothAlwaysUsageDescription` |
| `HKHealthStore` (read) | `NSHealthShareUsageDescription` |
| `HKHealthStore` (write) | `NSHealthUpdateUsageDescription` |
| `LAContext` (Face ID) | `NSFaceIDUsageDescription` |
| `CMMotionManager`, `CMPedometer` | `NSMotionUsageDescription` |
| `ASIdentifierManager.advertisingIdentifier` (IDFA) | `NSUserTrackingUsageDescription` + `ATTrackingManager` call |
| `NEHotspotConfiguration` | `NSLocationWhenInUseUsageDescription` |
| `NWPathMonitor` access to cellular details | `NSCellularPlanIdentifierUsageDescription` |

**Sniff.** New `import HealthKit`, `import CoreBluetooth`, `import LocalAuthentication`, `import CoreLocation`, etc., or new instantiations of any listed type in `+` lines. Read `Info.plist` from the worktree — if the required key isn't present, flag P1.

**Fix.** Add the usage-description string in `Info.plist`. Keep it concise and accurate; vague strings ("This app needs camera access.") get rejected during App Review. Describe the specific user-visible benefit:
```xml
<key>NSCameraUsageDescription</key>
<string>Scan your insurance card to auto-fill the form.</string>
```

---

## P1 — App Tracking Transparency (ATT) not called before tracking-relevant code

iOS 14.5+: any access to `advertisingIdentifier` (IDFA), or any SDK call that links user identity across apps/websites, must be preceded by `ATTrackingManager.requestTrackingAuthorization` with the user authorising tracking.

Cross-app tracking SDKs commonly affected:
- Facebook SDK (`FBSDKCoreKit`)
- AppsFlyer
- Adjust
- Branch
- Singular
- Mixpanel (when configured for ad-attribution use cases)

**Sniff.** Diff additions that:

1. Import an SDK in the list above
2. Call `ASIdentifierManager.shared().advertisingIdentifier`
3. Call known tracking-init functions (`FBSDKCoreKit.ApplicationDelegate.shared.application(...)`, `AppsFlyerLib.shared().start(...)`, etc.)

Cross-check whether `ATTrackingManager.requestTrackingAuthorization` is called anywhere in the app-launch path (search the worktree). If not, P1.

**Fix.** Add `NSUserTrackingUsageDescription` to `Info.plist`, then call ATT and gate tracking SDK init on the result:
```swift
ATTrackingManager.requestTrackingAuthorization { status in
    guard status == .authorized else { return }
    // initialise tracking SDKs only after user authorises
}
```

---

## P1 — Android dangerous permission added without a runtime request

When a permission in Android's *dangerous* set is added to `AndroidManifest.xml`, the runtime request flow must exist (since API 23 / Android 6.0). Missing it means the OS doesn't actually grant the permission — the API call silently fails or throws `SecurityException`.

Dangerous permissions:

| Permission | Notes |
|---|---|
| `CAMERA` | |
| `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS` | |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | |
| `ACCESS_BACKGROUND_LOCATION` (API 29+) | Must be requested separately AFTER fine/coarse |
| `RECORD_AUDIO` | |
| `READ_PHONE_STATE`, `CALL_PHONE`, `READ_CALL_LOG`, `WRITE_CALL_LOG` | |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | Deprecated on API 30+ — use scoped storage |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` (API 33+) | Replace storage permissions on Android 13+ |
| `BODY_SENSORS`, `BODY_SENSORS_BACKGROUND` (API 33+) | |
| `ACTIVITY_RECOGNITION` (API 29+) | |
| `POST_NOTIFICATIONS` (API 33+) | New on Android 13 |
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` (API 31+) | Replace legacy `BLUETOOTH` on Android 12+ |

**Sniff.** New `<uses-permission android:name="android.permission.X" />` in `AndroidManifest.xml` where X is in the table. Search the diff + worktree for any of:

- `ActivityCompat.requestPermissions(`
- `androidx.activity.result.contract.ActivityResultContracts.RequestPermission` / `RequestMultiplePermissions`
- `accompanist-permissions` / `rememberPermissionState` / `rememberMultiplePermissionsState`

If no runtime request flow exists for the new permission, P1.

**Fix.** Compose example with Accompanist:
```kotlin
val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
LaunchedEffect(Unit) {
    if (!cameraPermission.status.isGranted) {
        cameraPermission.launchPermissionRequest()
    }
}
```

For `ACCESS_BACKGROUND_LOCATION` specifically: must request foreground location first, get user approval, then request background separately — Android shows a system-mediated screen, not an in-app prompt.

---

## P2 — Play Store Data Safety drift on new data-collecting SDK

Google Play requires a *Data Safety* form declaring what user data is collected and shared. Adding an SDK that transmits user data without updating the form is a Play policy violation that can result in app removal.

Common data-collecting SDKs to flag on `build.gradle` additions:

- `com.google.firebase:firebase-analytics`
- `com.google.firebase:firebase-crashlytics`
- `com.mixpanel.android:mixpanel-android`
- `com.amplitude:android-sdk`
- `com.segment.analytics.android:analytics`
- `com.posthog.android:posthog-android`
- `com.appsflyer:af-android-sdk`
- `com.adjust.sdk:adjust-android`
- `io.branch.sdk.android:library`
- `com.facebook.android:facebook-android-sdk`
- `io.sentry:sentry-android`
- `com.bugsnag:bugsnag-android`
- `com.datadog.android:dd-sdk-android-rum`

**Sniff.** New `implementation(...)` / `api(...)` lines in any `build.gradle*` matching the patterns above. Post a P2 reminder (not a blocker — the reviewer cannot remotely verify the Play Console form):

> `[P2] New data-collecting SDK <name> added — confirm the Play Console Data Safety form lists the data types this SDK collects and shares. SDK docs typically list these. Reviewer cannot verify the form remotely.`

---

## P2 — Apple privacy: tracking SDK without `NSPrivacyTracking = true`

If `PrivacyInfo.xcprivacy` exists but `NSPrivacyTracking` is `false` while the project also uses an SDK known to track across apps/websites, the declaration is inconsistent.

**Sniff.** `PrivacyInfo.xcprivacy` with `<key>NSPrivacyTracking</key><false/>` AND a third-party tracking SDK present (Facebook, AppsFlyer, Branch, Adjust, Singular). P2 reminder.

---

## Output

```
[<file>:<line>] <severity> — Privacy — <rule> · <one-sentence fix>
```
