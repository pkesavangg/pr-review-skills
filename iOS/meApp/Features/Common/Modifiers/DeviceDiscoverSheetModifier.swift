//
//  DeviceDiscoverSheetModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/07/25.
//

import SwiftUI

struct DeviceDiscoverSheetModifier: ViewModifier {
    @Environment(\.appTheme) private var theme
    var height: CGFloat

    init(height: CGFloat = 400) {
        self.height = height
    }

    func body(content: Content) -> some View {
        content
            .presentationDetents([.height(height)])
            .presentationDragIndicator(.hidden)
            .presentationCornerRadius(.radiusXL)
            .presentationBackground(theme.backgroundPrimary)
            .interactiveDismissDisabled(true)
    }
}
