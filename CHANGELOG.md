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

