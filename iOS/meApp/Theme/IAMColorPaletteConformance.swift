//  AppPaletteConformance.swift
//  meApp
//
//  Created by Cursor AI
//

import SwiftUI
#if canImport(ggInAppMessagingPackage)
import ggInAppMessagingPackage
#endif

@available(iOS 17.0, macOS 14.0, *)
extension AppColors.Palette: IAMColorPalette {} 
