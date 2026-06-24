//
//  WeightOnlyModeOverlay.swift
//  meApp
//
//  Created by AI Assistant on 17/01/25.
//

import Combine
import SwiftUI

/// A view modifier that conditionally shows the WeightOnlyModeIndicator
/// based on whether any connected scales have weight-only mode enabled by others
struct WeightOnlyModeOverlay: ViewModifier {
    func body(content: Content) -> some View {
        ZStack {
            content
            WeightOnlyModeOverlayContent()
        }
    }
}

/// Internal view that handles the state and animation
private struct WeightOnlyModeOverlayContent: View {
    @State private var shouldShowIndicator = false
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel

    var body: some View {
        Group {
            // Hide FAB when AppSync camera screen is active
            if shouldShowIndicator && tabViewModel.selectedTab != .appsync {
                WeightOnlyModeIndicator()
                    .zIndex(1000) // Ensure it appears above other content
                    .transition(.opacity.combined(with: .scale))
                    .animation(.easeInOut(duration: 0.3), value: shouldShowIndicator)
            }
        }
        .onReceive(BluetoothService.shared.showWeightOnlyModeAlertPublisher) { shouldShow in
            shouldShowIndicator = shouldShow
        }
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
    .environmentObject(BottomTabBarViewModel())
}
