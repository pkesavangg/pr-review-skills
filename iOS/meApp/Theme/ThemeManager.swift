//
//  ThemeManager.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/05/25.
//


import Foundation
import SwiftUI

/// Singleton class responsible for managing the current color scheme and dark mode settings of the app.
final class ThemeManager: ObservableObject {
    static let shared = ThemeManager()

    /// Currently active app color scheme.
    @Published var currentColorScheme: AppColorScheme = .primary
        
    /// Flag indicating whether dark mode is enabled. This is persisted using UserDefaults.
    @Published var isDarkMode: Bool {
        didSet {
            UserDefaults.standard.set(isDarkMode, forKey: "isDarkMode")
        }
    }

    /// Private initializer to enforce singleton pattern. Loads saved dark mode state from UserDefaults.
    private init() {
        // Load initial value from UserDefaults
        isDarkMode = UserDefaults.standard.bool(forKey: "isDarkMode")
    }
}
