//
//  AppThemeKey.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/05/25.
//

import SwiftUI

/// Defines a custom environment key to inject the current app theme palette into the view hierarchy.
private struct AppThemeKey: EnvironmentKey {
    static let defaultValue: AppColors.Palette = AppColors.Theme.primary.palette
}

/// Extends EnvironmentValues to provide a typed accessor for the app's theme palette.
extension EnvironmentValues {
    var appTheme: AppColors.Palette {
        get { self[AppThemeKey.self] }
        set { self[AppThemeKey.self] = newValue }
    }
}

/// ViewModifier that injects the current theme palette into the environment using the ThemeManager.
struct ThemeableModifier: ViewModifier {
    @EnvironmentObject var themeManager: ThemeManager
    
    func body(content: Content) -> some View {
        let theme = themeFrom(themeManager.currentColorScheme)
        content.environment(\.appTheme, theme.palette)
    }
    
    private func themeFrom(_ scheme: AppColorScheme) -> AppColors.Theme {
        switch scheme {
        case .primary: return .primary
        }
    }
}

/// View extension for applying the `ThemeableModifier`.
extension View {
    func themeable() -> some View {
        modifier(ThemeableModifier())
    }
}
