# SettingsStore Test Coverage

## Scope
Unit tests for `SettingsStore` focus on regression safety for:
- account-derived display state
- feed badge and message-title state
- browser and picker presentation state
- edit-profile and weightless form population
- discard-confirmation alerts
- forgot-password and CSV export alert flows
- validation and no-op handling for settings input

## Cases Covered
1. Initial state hydrates active-account state, logout-all visibility, and entry presence
2. Derived texts reflect active account settings
3. `weightlessText` shows the formatted on-state value
4. `messagesTitleText` includes unread count only when the badge is enabled
5. Browser presentation binding clears browser flags and URL when dismissed
6. `openPrivacy`, `openTerms`, and `openGreaterGoods` set browser state
7. `populateEditFormIfNeeded` loads account values when the form is pristine
8. `populateEditFormIfNeeded` preserves dirty edits
9. `populateWeightlessFormIfNeeded` clears values when there is no active account
10. `showHeightPicker` respects the active account unit
11. `updateHeight` rejects invalid values and shows an error toast
12. `confirmDiscardPasswordChanges` returns `false` when the user cancels
13. `confirmDiscardProfileChanges` returns `true` when the user confirms exit
14. `sendForgotPasswordEmail` success shows loader, requests reset, and shows toast
15. `handleExport` shows alert and primary action exports CSV
16. Browser URL fallback uses the Greater Goods link when no explicit URL is set
17. Weightless and notification derived text covers off, partial, and no-weight variants
18. Pristine discard checks return immediately for password and goal flows
19. Cancel-path discard confirmation is covered for profile and goal flows
20. Dirty change-password exit confirmation resets state and navigates back on exit
21. Password save error handling covers server-error messaging
22. Profile save error handling covers no-internet suppression and pending-scale-update alerts
23. Weight, activity, gender, and height updates cover `USER_SELECTION_IN_PROGRESS` alert behavior
24. Goal form population preserves in-flight edits once the form is dirty
25. Goal save covers maintain-goal payload creation, latest-entry fallback, pending-scale-update alerts, and failure toasts
26. Forgot-password failure shows retry messaging
27. CSV export no-internet failure skips the generic error toast

## Dependency Strategy
Tests use the real `SettingsStore` with test doubles for account, entry, feed, Bluetooth, integration, and logger services.

Because `SettingsStore` mixes protocol-backed services with concrete `NotificationHelperService` and `GoalAlertService` dependencies, the suite uses:
- a shared `MockFeedService` for unread count, feed settings, and badge publisher assertions
- a concrete `TestNotificationHelperService` subclass for assertions
- `GoalAlertService.shared` only for dependency satisfaction
- serialized execution and no container reset to avoid cross-suite DI breakage

## Coverage
`SettingsStore` test coverage is 93.3%.