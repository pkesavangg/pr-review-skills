//
//  AppState.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//


import Foundation
import Combine

/// A centralized app state object responsible for managing and exposing shared services.
/// This allows for easier dependency injection and access to global services across the app.
@MainActor
final class AppState {
    /// Manages the app's visual theme.
    let themeManager = Theme.shared
    
    /// Registers and manages core application services.
    let serviceRegistry = ServiceRegistry.shared
    
    /// Monitors network connectivity status.
    let networkMonitor = NetworkMonitor.shared
}