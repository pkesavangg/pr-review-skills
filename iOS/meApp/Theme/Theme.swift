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
    
    /// Currently active account ID for theme persistence
    private var activeAccountId: String?
        
    @Published var appearanceMode: AppearanceMode {
        didSet {
            saveAppearanceMode()
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
        // Initialize with system default, will be updated when account is set
        appearanceMode = .system
        loadGlobalAppearanceMode()
        updateWindowInterfaceStyle()
    }
    
    /// Sets the active account and loads account-specific theme preferences
    func setActiveAccount(_ accountId: String?) {
        self.activeAccountId = accountId
        loadAppearanceModeForAccount()
    }
    
    /// Loads appearance mode for the current active account
    func loadAppearanceModeForAccount() {
        guard let accountId = activeAccountId else {
            // No account, load global setting
            loadGlobalAppearanceMode()
            return
        }
        
        let key = appearanceModeKey(for: accountId)
        if let savedMode = UserDefaults.standard.string(forKey: key),
           let mode = AppearanceMode(rawValue: savedMode) {
            // Account has saved preference, use it
            appearanceMode = mode
        } else {
            // New account, use default system setting (not current setting)
            appearanceMode = .system
        }
    }
    
    /// Loads global appearance mode setting
    private func loadGlobalAppearanceMode() {
        if let savedMode = UserDefaults.standard.string(forKey: "appearanceMode"),
           let mode = AppearanceMode(rawValue: savedMode) {
            appearanceMode = mode
        } else {
            appearanceMode = .system
        }
    }
    
    /// Saves the current appearance mode for the active account
    private func saveAppearanceMode() {
        if let accountId = activeAccountId {
            // Save account-specific preference
            let key = appearanceModeKey(for: accountId)
            UserDefaults.standard.set(appearanceMode.rawValue, forKey: key)
        } else {
            // Save global preference
            UserDefaults.standard.set(appearanceMode.rawValue, forKey: "appearanceMode")
        }
    }
    
    /// Generates account-specific UserDefaults key for appearance mode
    private func appearanceModeKey(for accountId: String) -> String {
        return "appearanceMode_\(accountId)"
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
