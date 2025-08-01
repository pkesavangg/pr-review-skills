## 5.0.0 - 08/01/2025

### Added

- Created a reusable select button screens for wifi and bluetooth setup. (MA-768)
- Created wifi setup complete - UI. (MA-794)
- Implemented wifi setup with wifi service. (MA-787)
- Implemented A3 Scale setup with service. (MA-782)

### Changed

- Refactored setup loader. (MA-796)
- Refactored integration settings. (MA-787)
- Refactored Scale setup structure. (MA-769)
- Refactored graph view. (MA-828)

### Fixed

- Fixed location switch permission crash issue. (MA-776)
- Resolved bluetooth scale connection and deletion issue. (MA-840, MA-842)
- Fixed Push notification , MileStone card display and BLE connection status issue. (MA-829 , 833 , 839)

## UNRELEASED - 07/04/2025

### Added

#### Interactive Entry Graph (Using Vico3)

- Implemented interactive entry graph with smooth scrolling and data averaging
- Added swipe navigation through time ranges with optimized performance
- Implemented intelligent point selection with contextual data display
  - Tap to highlight specific data points with exact date and value
  - Auto-adjusting view context (week/month/year/all-time)
  - Tap outside to reset view and show full context
- Added seamless view switching with selected point as center
- Implemented dual display support for weight and weight-less modes
- Optimized performance with deferred average calculations during scrolling

#### Account Management & Authentication

- Implemented account switching feature with remove account and account limit reached alerts
- Handled background logout with alert
- Implemented account switch info modal
- Added function to return form value as data type

#### Scale Management

- Implemented Add & Edit scale screen with My scale view and model number help popup
- Implemented Scale list view with filters
- Created Device service
- Implemented Scale Details Screen

#### History & Dashboard

- Implemented history graph module with metrics integration
- Implemented milestones section with Room DB integration
- Implemented auto calculation of BMI in manual entry
- Implemented wiggle animation and on-edit behavior
- Implemented dashboard stats sync and rearrangement
- Refactored dashboard key datastore structure
- Refactored history screen

#### Settings & UI Components

- Implemented goal screen and account delete functionality
- Implemented weightless screen
- Implemented help and debug menu screens
- Updated swipe-to-action to dynamically accept input

#### Integration & Migration

- Migrated Appsync Ionic plugin to Android library
- Migrated Ionic WiFi Plugin to Android Library
- Created UI for integration screen
- Implemented manual entry configuration

### Changed

- Refactored static declaration and removed boilerplate code in Login and Signup Screens
- Enhanced navigation restriction handling in stack
- Optimized data visualization in history graph module
- Enhanced graph view transitions and data point handling

### Fixed

- Improved account management error handling
- Enhanced data synchronization reliability
- Optimized graph performance during view transitions

### Technical

- Integrated Vico3 library for advanced graph visualization

# 5.0.0 - 07/04/2025

Initial release of the MeApp Android application with core functionality.
