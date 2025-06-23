//
//  SettingsItemConfig.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import SwiftUI

// MARK: - Settings Item Configuration
/// Configuration for a settings item in the settings list.
struct SettingsItemConfig {
    let title: String
    var value: String? = nil
    var chevronType: ChevronType = .right
    var isDestructive: Bool = false
    var toggleBinding: Binding<Bool>? = nil
    var showDot: Bool = false
    var dotColor: Color? = nil
    var onTap: (() -> Void)? = nil
}
