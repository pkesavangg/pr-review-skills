//
//  WeightOnlyModeOverlay.swift
//  meApp
//
//  Created by AI Assistant on 17/01/25.
//

import SwiftUI
import Combine

/// A view modifier that conditionally shows the WeightOnlyModeIndicator
/// based on whether any connected scales have weight-only mode enabled by others
struct WeightOnlyModeOverlay: ViewModifier {
    @Injector private var bluetoothService: BluetoothService

    func body(content: Content) -> some View {
        ZStack {
            content
            WeightOnlyModeOverlayContent()
        }
    }
}

/// Internal view that handles the state and animation
private struct WeightOnlyModeOverlayContent: View {
//    let bluetoothService: BluetoothService
    @State private var shouldShowIndicator = true

    var body: some View {
        Group {
            if shouldShowIndicator {
                WeightOnlyModeIndicator()
                    .zIndex(1000) // Ensure it appears above other content
                    .transition(.opacity.combined(with: .scale))
                    .animation(.easeInOut(duration: 0.3), value: shouldShowIndicator)
            }
        }
//        .onReceive(bluetoothService.showWeightOnlyModeAlertPublisher) { shouldShow in
//            withAnimation(.easeInOut(duration: 0.3)) {
//                shouldShowIndicator = shouldShow
//            }
//        }
    }
}

// MARK: - View Extension

extension View {
    /// Conditionally overlays the WeightOnlyModeIndicator when scales have weight-only mode enabled by others
    func withWeightOnlyModeIndicator() -> some View {
        self.modifier(WeightOnlyModeOverlay())
    }
}

// MARK: - Preview

#Preview {
    VStack {
        Text("Main Content")
            .font(.title)
            .padding()

        Spacer()

        Text("Other content below")
            .font(.body)
            .padding()

        Spacer()
    }
    .withWeightOnlyModeIndicator()
    .environmentObject(Theme.shared)
}
