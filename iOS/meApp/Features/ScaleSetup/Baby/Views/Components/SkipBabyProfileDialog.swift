//
//  SkipBabyProfileDialog.swift
//  meApp
//

import SwiftUI

/// "Skip Baby Profile?" confirmation dialog.
/// Note: This dialog is presented as a native .alert on BabyScaleSetupScreen
/// rather than as a standalone view. This file exists for documentation and
/// potential future use as a custom dialog if the design changes.
///
/// Current implementation uses:
/// ```swift
/// .alert(lang.SkipDialog.title, isPresented: $store.showSkipDialog) { ... }
/// ```
/// See BabyScaleSetupScreen.swift for the active implementation.
struct SkipBabyProfileDialogStrings {
    static let title = BabyScaleSetupStrings.SkipDialog.title
    static let message = BabyScaleSetupStrings.SkipDialog.message
    static let cancel = BabyScaleSetupStrings.SkipDialog.cancel
    static let finishSetup = BabyScaleSetupStrings.SkipDialog.finishSetup
}
