//  TabDeactivationEnvironment.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 17/06/25.
//
//  Provides an EnvironmentValue that allows a child tab view to register a
//  handler which determines whether the tab can be deactivated. The parent
//  `BottomTabBarView` sets this value for each tab and stores the handler so
//  that it can query the currently-visible tab before switching to a new one.
//

import SwiftUI

// MARK: - Environment key

/// Registers a handler that is invoked when the user attempts to switch away
/// from the current tab. The handler must return `true` if it is safe to
/// deactivate the tab (e.g. there are no unsaved changes) or `false` to cancel
/// the navigation.
struct TabDeactivationRegisterKey: EnvironmentKey {
    /// Default implementation does nothing – if no handler is registered the
    /// tab switch will always succeed.
    static let defaultValue: (@escaping () async -> Bool) -> Void = { _ in }
}

extension EnvironmentValues {
    /// Allows a tab view to register a deactivation handler with the parent
    /// `BottomTabBarView`.
    var registerTabDeactivationHandler: (@escaping () async -> Bool) -> Void {
        get { self[TabDeactivationRegisterKey.self] }
        set { self[TabDeactivationRegisterKey.self] = newValue }
    }
} 
