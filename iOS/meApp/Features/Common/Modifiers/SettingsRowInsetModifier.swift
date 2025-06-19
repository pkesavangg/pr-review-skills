//
//  SettingsRowInsetModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//
import SwiftUI

// MARK: - Settings Row Inset Modifier
/// A view modifier that applies custom insets to a settings row.
struct SettingsRowInsetModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .listRowInsets(
                EdgeInsets(
                    top: 11,
                    leading: .spacingSM,
                    bottom: 11,
                    trailing: .spacingSM
                )
            )
    }
}
