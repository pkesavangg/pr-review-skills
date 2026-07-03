//
//  AccessibilityID+AppSync.swift
//  AppSync feature — body-composition scanning tab.
//
//  NOTE: the AppSync scanner surface itself (camera view + its Close / Manual-entry /
//  scan controls) is rendered by `AppSyncView` inside the external `AppSyncPackage`
//  SPM dependency, so those inner controls must be tagged in that package, not here.
//  The app-side anchor is the scanner wrapper's root, below.
//

extension AccessibilityID {
    // MARK: - AppSync
    static let appSyncScannerRoot = "appsync_scanner_root"
}
