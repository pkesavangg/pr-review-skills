# meApp iOS

This is the iOS application for the meApp project.

## Project Overview
meApp is an iOS application built using Swift and SwiftUI. It includes core app functionality, UI assets, and test targets.

## Directory Structure
- `meApp/` - Main app source code
- `meAppTests/` - Unit tests
- `meAppUITests/` - UI tests
- `meApp.xcodeproj/` - Xcode project file

## Getting Started
1. Clone the repository.
2. Open `meApp.xcodeproj` in Xcode.
3. Build and run the app on a simulator or device.

## Simulator Support

The `GGBluetoothSDKFramework.xcframework` and `smartConfig.xcframework` (used by the Wi-Fi scale package) ship with device-only binary slices. To build and run on the iOS Simulator, switch both packages to the `simulator-support` branch, which includes stub frameworks for `arm64` and `x86_64` simulator architectures.

### Setup

In Xcode, update the SPM dependency versions for these two packages:

| Package | Branch |
|---------|--------|
| `ggBluetoothNativeLibrary` | `simulator-support` |
| `ggWifiScalePackage` | `simulator-support` |

**Steps:**
1. In Xcode, go to **File > Packages > Reset Package Caches** to clear any cached artifacts.
2. Select the project in the navigator, then go to **Package Dependencies**.
3. For `ggBluetoothNativeLibrary`, double-click the version rule and change it to **Branch → `simulator-support`**.
4. For `ggWifiScalePackage`, double-click the version rule and change it to **Branch → `simulator-support`**.
5. Resolve packages (**File > Packages > Resolve Package Versions**).
6. Build and run on any iOS Simulator destination.

> **Note:** The simulator stubs provide only the type signatures and empty implementations needed to compile. Bluetooth and Wi-Fi scale functionality will not work on the simulator — use a physical device for testing scale features. Switch back to `main` before submitting PRs or cutting releases.

## Development
- The app uses Swift and SwiftUI.
- All dependencies are managed via Xcode (no CocoaPods or Carthage by default).
- For custom assets, add them to `Assets.xcassets`.

## Testing
- Run unit tests with `Cmd+U` in Xcode.
- UI tests are located in the `meAppUITests` directory.
- See `docs/TESTING.md` for the full testing conventions, coverage requirements, and how to run/export coverage reports.



