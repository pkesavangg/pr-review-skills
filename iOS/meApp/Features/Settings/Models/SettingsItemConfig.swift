//
//  SettingsItemConfig.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

// MARK: - Settings Item Configuration
/// Configuration for a settings item in the settings list.
struct SettingsItemConfig {
    let title: String
    var value: String? = nil
    var canShowChevron: Bool = true
    var isDestructive: Bool = false
    var onTap: (() -> Void)? = nil
}
