// iOS/meApp/Features/Settings/Strings/WeightlessStrings.swift
// Localization constants for Weightless settings screen

import Foundation

struct WeightlessStrings {
    static let title = "Weightless"
    static let subtitle = "Track your progress without a number connected to it."
    static let description = "Instead of your current weight, you will see + or - progress reflective of the change from your chosen weight."
    static let modeLabel = "Weightless Mode"
    static let weightPlaceholderLbs = "weightless weight lbs"
    static let weightPlaceholderKg = "weightless weight kg"
    static let save = CommonStrings.save
    // Alerts
    struct ExitAlert {
        static let title = "Confirm"
        static let message = "You have unsaved changes. Are you sure you want to exit?"
        static let exitButton = "Exit"
        static let returnButton = "Return"
    }
} 