//
//  ThemeManager.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/05/25.
//


import Foundation
import SwiftUI

/// Singleton class responsible for managing the current color scheme and dark mode settings of the app.
final class Theme: ObservableObject {
    static let shared = Theme()
    @Published var systemColorScheme: ColorScheme = .light

    /// Currently active app color scheme.
    @Published var currentColorScheme: AppColorScheme = .primary
        
    @Published var appearanceMode: AppearanceMode {
        didSet {
            UserDefaults.standard.set(appearanceMode.rawValue, forKey: "appearanceMode")
            updateWindowInterfaceStyle()
        }
    }
    
    /// Computed property for backward compatibility
    var isDarkMode: Bool {
        get {
            switch appearanceMode {
            case .dark: return true
            case .light: return false
            case .system: return systemColorScheme == .dark
            }
        }
        set {
            appearanceMode = newValue ? .dark : .light
        }
    }
    
    /// Returns the ColorScheme to be used by SwiftUI views
    func getPreferredAppearanceMode() -> ColorScheme? {
        return appearanceMode.colorScheme
    }
    
    /// Syncs the system color scheme with the theme manager
    func syncWithSystemColorScheme(_ systemColorScheme: ColorScheme) {
        self.systemColorScheme = systemColorScheme
    }

    /// Private initializer to enforce singleton pattern. Loads saved appearance mode from UserDefaults.
    private init() {
        // Load initial value from UserDefaults
        if let savedMode = UserDefaults.standard.string(forKey: "appearanceMode"),
           let mode = AppearanceMode(rawValue: savedMode) {
            appearanceMode = mode
        } else {
            // Default to system mode if no saved preference
            appearanceMode = .light
        }
        updateWindowInterfaceStyle()
    }
    
    /// Applies the interface style to the window
    private func updateWindowInterfaceStyle() {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        guard let window = windowScene.windows.first else { return }
        
        switch appearanceMode {
        case .system:
            window.overrideUserInterfaceStyle = .unspecified
        case .light:
            window.overrideUserInterfaceStyle = .light
        case .dark:
            window.overrideUserInterfaceStyle = .dark
        }
    }
}
