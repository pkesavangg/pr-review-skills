//
//  ActionListItemConfig.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import SwiftUI

// MARK: - Action Item Configuration
/// Configuration for a settings item in the settings list.
struct ActionListItemConfig {
    let title: String
    var value: String? = nil
    var chevronType: ChevronType = .right
    var isDestructive: Bool = false
    var toggleBinding: Binding<Bool>? = nil
    var showDot: Bool = false
    var dotColor: Color? = nil
    /// Optional leading icon shown before the title (e.g., status indicator).
    /// If provided, `showDot` will be ignored.
    var leadingIcon: AnyView? = nil
    var onTap: (() -> Void)? = nil
}