//
//  SettingsRowInsetModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//
import SwiftUI

// MARK: - List Row Inset Modifier
/// A view modifier that applies custom insets to a settings row.
struct ListRowInsetModifier: ViewModifier {
    let top: CGFloat
    let bottom: CGFloat
    let leading: CGFloat
    let trailing: CGFloat

    func body(content: Content) -> some View {
        content
            .listRowInsets(
                EdgeInsets(
                    top: top,
                    leading: leading,
                    bottom: bottom,
                    trailing: trailing
                )
            )
    }
}
