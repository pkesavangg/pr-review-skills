//
//  DeviceDiscoverSheetModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/07/25.
//


import SwiftUI

struct DeviceDiscoverSheetModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .presentationDetents([.height(400)])
            .presentationDragIndicator(.hidden)
            .presentationCornerRadius(.radiusXL)
            .interactiveDismissDisabled(true)
    }
}
