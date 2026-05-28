# 5.0.2 - 05/22/2026

## Android

Added

- [MA-3939](https://greatergoods.atlassian.net/browse/MA-3939) Added a one-time educational modal hinting that the weight graph is scrollable.
- [MA-3887](https://greatergoods.atlassian.net/browse/MA-3887) Synced Wi-Fi and R4 scale entries to Health Connect.
- [MA-4003](https://greatergoods.atlassian.net/browse/MA-4003) Added a Restore Account placeholder to Scale Setup.

Changed

- [MA-3938](https://greatergoods.atlassian.net/browse/MA-3938) Updated dashboard graphs to use the latest daily readings and optimised Health Connect sync, with daywise-latest SQL and improved chart empty-state transitions.
- [MA-3965](https://greatergoods.atlassian.net/browse/MA-3965) Showed daily average for non-latest day points on the Week/Month graph and routed MetricInfo label through GraphLabelHelper.
- [MA-3930](https://greatergoods.atlassian.net/browse/MA-3930) Improved Health Connect integration state handling and stability.
- [MA-3911](https://greatergoods.atlassian.net/browse/MA-3911) Implemented immediate theme switching and fixed system theme propagation.
- [MA-3906](https://greatergoods.atlassian.net/browse/MA-3906) Automated native library build and enabled 16KB page alignment.
- [MA-3900](https://greatergoods.atlassian.net/browse/MA-3900) Handled unknown device SKUs more gracefully, removing the hardcoded default SKU.
- [MA-3885](https://greatergoods.atlassian.net/browse/MA-3885) Improved AppSync scan reliability and logging via a logger bridge and corrected frame orientation in the native detector.
- [MA-3874](https://greatergoods.atlassian.net/browse/MA-3874) Refined Ionic-to-native migration reliability and logging.
- [MA-3859](https://greatergoods.atlassian.net/browse/MA-3859) Capped system font scale at 1.3x to prevent UI breakage at maximum font size.
- [MA-3365](https://greatergoods.atlassian.net/browse/MA-3365) Converted PNG drawables to WebP for APK size reduction.
- [MA-3953](https://greatergoods.atlassian.net/browse/MA-3953) Showed the graph scroll hint after an in-session account switch.
- [MA-3945](https://greatergoods.atlassian.net/browse/MA-3945) Improved height precision and rounding for BLE profile synchronisation and BMI calculations.
- [MA-3975](https://greatergoods.atlassian.net/browse/MA-3975) Updated Have a Question modal spacing to align with design specifications.
- [MA-3901](https://greatergoods.atlassian.net/browse/MA-3901) Bumped ggBluetoothAndroid library (1.6.3 → 1.6.5).
- [MA-4003](https://greatergoods.atlassian.net/browse/MA-4003) Updated Scale Setup UI padding.

Fixed

- [MA-3998](https://greatergoods.atlassian.net/browse/MA-3998) Fixed visceral fat manual entry being stored as one-tenth of the entered value.
- [MA-3936](https://greatergoods.atlassian.net/browse/MA-3936) Fixed Health Connect sync failures by batching health data inserts to stay within Health Connect limits.
- [MA-3919](https://greatergoods.atlassian.net/browse/MA-3919) Rejected invalid weightless weight when tapping keyboard Done.
- [MA-3909](https://greatergoods.atlassian.net/browse/MA-3909) Ignored stale Health Connect DataStore entries when checking user conflict.
- [MA-3894](https://greatergoods.atlassian.net/browse/MA-3894) Fixed broken Texas A&M Health resource link in Body Water metric info.
- [MA-3854](https://greatergoods.atlassian.net/browse/MA-3854) Fixed app crash on rotation or other configuration changes by upgrading Navigation3 to 1.1.1.
- [MA-3929](https://greatergoods.atlassian.net/browse/MA-3929) Fixed delayed FINISH response on the Wi-Fi scale setup troubleshooting screen.

## iOS

Added

- [MA-3925](https://greatergoods.atlassian.net/browse/MA-3925) Added a one-time educational modal hinting that the weight graph is scrollable.
- [MA-3984](https://greatergoods.atlassian.net/browse/MA-3984) Replaced the graph scroll hint GIF with a native SwiftUI animated demo.

Changed

- [MA-3914](https://greatergoods.atlassian.net/browse/MA-3914) Renamed the "Default Graph Range" setting to "Default Graph View".
- [MA-3937](https://greatergoods.atlassian.net/browse/MA-3937) Showed the latest entry instead of a daily average when a day has multiple weigh-ins.
- [MA-3956](https://greatergoods.atlassian.net/browse/MA-3956) Migrated Swift package references from the dmdbrands org to gg-engineering.
- [MA-3941](https://greatergoods.atlassian.net/browse/MA-3941) Reduced integration sync time for large accounts by chunking the HealthKit full sync and deduping leanBodyMass entries.
- [MA-3845](https://greatergoods.atlassian.net/browse/MA-3845) Improved Week graph performance for accounts with large datasets, including split state slices, cached calendars, and refactored chart padding.
- [MA-3886](https://greatergoods.atlassian.net/browse/MA-3886) Improved Apple Health integration to reliably sync entries from Wi-Fi and R4 scales.

Fixed

- [MA-4005](https://greatergoods.atlassian.net/browse/MA-4005) Fixed the metric info screen showing incorrect total averages for weight/BMI and missing values for other body metrics.
- [MA-4004](https://greatergoods.atlassian.net/browse/MA-4004) Fixed several body metrics missing from the exported CSV data file.
- [MA-3981](https://greatergoods.atlassian.net/browse/MA-3981) Fixed intermittent issue where the latest entry graph data point was not displayed correctly in graph views.
- [MA-3977](https://greatergoods.atlassian.net/browse/MA-3977) Fixed the dashboard graph not reliably selecting the latest window entry on tab switch.
- [MA-3951](https://greatergoods.atlassian.net/browse/MA-3951) Fixed the scrollable graph onboarding modal arrow icon colour to match the design mock.
- [MA-3921](https://greatergoods.atlassian.net/browse/MA-3921) Fixed keyboard Done button cropping on smaller iPhones and grey button decoration when the Button Shapes accessibility setting was enabled.
- [MA-3917](https://greatergoods.atlassian.net/browse/MA-3917) Fixed the exit confirmation alert appearing in Goal Settings even when no changes were made.
- [MA-3898](https://greatergoods.atlassian.net/browse/MA-3898) Fixed app crash inside SwiftData model context save from concurrent background contexts during entry sync.
- [MA-3897](https://greatergoods.atlassian.net/browse/MA-3897) Fixed app crash during initialisation caused by an Injector force-unwrap race in FeedService re-registration.
- [MA-3894](https://greatergoods.atlassian.net/browse/MA-3894) Fixed broken Texas A&M Health resource link in Body Water metric info.
- [MA-3891](https://greatergoods.atlassian.net/browse/MA-3891) Fixed the auto-selected graph data point becoming unselected when Weightless mode was toggled.
- [MA-3882](https://greatergoods.atlassian.net/browse/MA-3882) Fixed R4 scale body metrics not updating after changing gender, age, or height, by snapshotting scales before SDK push to survive transient empty publishes.
- [MA-3928](https://greatergoods.atlassian.net/browse/MA-3928) Fixed invalid manual entries navigating to the Dashboard when tapping keyboard Next on iPad.

# 5.0.1 - 05/06/2026

## Android

Added

- [MA-3843](https://greatergoods.atlassian.net/browse/MA-3843) Added support for SKU 0340 and optimised AppSync scale detection.
- [MA-1178](https://greatergoods.atlassian.net/browse/MA-1178) Set the AppSync scale camera zoom level based on user height and persisted the last-used zoom per account.
- [MA-3850](https://greatergoods.atlassian.net/browse/MA-3850) Added a per-account Default Graph Range setting in Account Settings.

Changed

- [MA-3853](https://greatergoods.atlassian.net/browse/MA-3853) Adopted a scroll-aware range provider for the graph and sourced the default graph segment synchronously on dashboard init.
- [MA-3841](https://greatergoods.atlassian.net/browse/MA-3841) Updated the Vico chart library to 3.0.4 and bounded chart axis tick generation to the visible range.

Fixed

- [MA-3842](https://greatergoods.atlassian.net/browse/MA-3842) Fixed metric chart not resetting to the latest entry when switching between Week, Month, and Year.
- [MA-3830](https://greatergoods.atlassian.net/browse/MA-3830) Fixed Edit Profile birthday reverting after save for users in US timezones.
- [MA-3852](https://greatergoods.atlassian.net/browse/MA-3852) Fixed app crash during data migration when upgrading from an older version, using keyset paging and resume checkpoints.
- [MA-3880](https://greatergoods.atlassian.net/browse/MA-3880) Re-anchored the Week graph marker to the latest entry after an add or delete.

## iOS

Added

- [MA-3849](https://greatergoods.atlassian.net/browse/MA-3849) Made Month the default graph tab and added a Default Graph Tab option in Settings.
- [MA-3863](https://greatergoods.atlassian.net/browse/MA-3863) Persisted the AppSync camera zoom level so it is restored on next open.

Changed

- [MA-3839](https://greatergoods.atlassian.net/browse/MA-3839) Made uniform segmented-tab font scaling opt-in and widened the streak row gap on small phones to avoid label truncation on iPhone 17.
- [MA-3858](https://greatergoods.atlassian.net/browse/MA-3858) Capped Dynamic Type font scaling to prevent UI breakage at max system font size.

Fixed

- [MA-3837](https://greatergoods.atlassian.net/browse/MA-3837) Fixed metric chart not resetting to the latest entry when switching between Week, Month, and Year.
- [MA-3829](https://greatergoods.atlassian.net/browse/MA-3829) Fixed Edit Profile birthday reverting after save for users in US timezones.
- [MA-3582](https://greatergoods.atlassian.net/browse/MA-3582) Fixed the exit confirmation alert continuing to appear in Display Metrics and Scale Modes after clearing the scale name and exiting.
- [MA-3840](https://greatergoods.atlassian.net/browse/MA-3840) Fixed AppSync scale disappearing from the dashboard and forcing users to re-add the scale.
- [MA-3851](https://greatergoods.atlassian.net/browse/MA-3851) Fixed unsynced entries appearing in Apple Health after deleting a synced entry.
- [MA-3857](https://greatergoods.atlassian.net/browse/MA-3857) Fixed app crash during scale sync when the server assigned a new ID to a locally created scale.
- [MA-3865](https://greatergoods.atlassian.net/browse/MA-3865) Fixed incorrect metric values shown for the selected latest entry during tab switching and app initialisation.
- [MA-3884](https://greatergoods.atlassian.net/browse/MA-3884) Fixed Scale Manual segmented tabs not scrolling and misaligning under Dynamic Type.

# 5.0.0 - 04/21/2026

This release marks the migration of Weight Gurus from Ionic to fully native — SwiftUI on iOS and Jetpack Compose on Android — built on top of the existing 4.x feature set.

## Android

Added

- Multi-Account / Switch Account — Added the ability to add and manage multiple accounts within the app, and to switch between accounts seamlessly without logging out.
- In-App Messaging — Introduced the landing page screen in Feeds.

Changed

- Migrated the Android app from Ionic to native Jetpack Compose, layered on top of the existing 4.x functionality.
- Revamped the Dashboard with scrollable, interactive native charts.
- Redesigned the body composition metrics display and added new progress metrics.

Fixed

- None

## iOS

Added

- Multi-Account / Switch Account — Added the ability to add and manage multiple accounts within the app, and to switch between accounts seamlessly without logging out.
- In-App Messaging — Introduced the landing page screen in Feeds.

Changed

- Migrated the iOS app from Ionic to native SwiftUI, layered on top of the existing 4.x functionality.
- Revamped the Dashboard with scrollable, interactive native charts.
- Redesigned the body composition metrics display and added new progress metrics.
- Improved the HealthKit integration flow with integration modals (add / finish / out-of-sync).

Fixed

- None

