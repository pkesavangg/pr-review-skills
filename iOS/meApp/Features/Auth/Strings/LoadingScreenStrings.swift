//
//  LoadingScreenStrings.swift
//  meApp
//
//  Created by Lakshmi Priya on 13/06/25.
//

import Foundation

/// LoadingScreenStrings provides a centralized collection of string constants used throughout the loading screen in the application.
struct LoadingScreenStrings {
    static let loading = "Loading"
    static let copyright = "me.health by Greater Goods"
    static let versionPrefix = "Version"
    static let versionKey = "CFBundleShortVersionString"

    // MARK: - Accessibility (VoiceOver) — spoken text only, not shown on screen
    static let accLogoLabel = "Weight Gurus, my everyday health"
    static let accLoadingLabel = "Loading, please wait"
}
