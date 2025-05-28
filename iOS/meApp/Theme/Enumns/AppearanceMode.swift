//
//  AppearanceMode.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/05/25.
//


import SwiftUI

/// Enum representing the different appearance modes available in the app
enum AppearanceMode: String, CaseIterable {
    case light = "Light"
    case dark = "Dark"
    case system = "System"
    
    var colorScheme: ColorScheme? {
        switch self {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil // nil means follow system setting
        }
    }
}