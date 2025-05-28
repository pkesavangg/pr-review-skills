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
        
    @Published var appearanceMode: AppearanceMode {
        didSet {
            UserDefaults.standard.set(appearanceMode.rawValue, forKey: "appearanceMode")
        }
    }
    
    /// Computed property for backward compatibility
    var isDarkMode: Bool {
        get {
            switch appearanceMode {
            case .dark: return true
            case .light: return false
            case .system:
                // For system mode, we'll return false here
                // The actual appearance will be determined by the system
                // when we return nil from getPreferredColorScheme()
                return false
            }
        }
        set {
            appearanceMode = newValue ? .dark : .light
        }
    }
    
    /// Returns the ColorScheme to be used by SwiftUI views
    func getPreferredColorScheme() -> ColorScheme? {
        return appearanceMode.colorScheme
    }

    /// Private initializer to enforce singleton pattern. Loads saved appearance mode from UserDefaults.
    private init() {
        // Load initial value from UserDefaults
        if let savedMode = UserDefaults.standard.string(forKey: "appearanceMode"),
           let mode = AppearanceMode(rawValue: savedMode) {
            appearanceMode = mode
        } else {
            // Default to system mode if no saved preference
            appearanceMode = .system
        }
    }
}
