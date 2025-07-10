//
//  WeightOnlyModeIndicator.swift
//  meApp
//
//  Created by AI Assistant on 17/01/25.
//

import SwiftUI

/// A draggable floating action button that indicates weight-only mode is enabled by others
/// Equivalent to Angular's WeightOnlyModeIndicatorComponent
struct WeightOnlyModeIndicator: View {
    @Environment(\.appTheme) private var theme
    @StateObject private var positionStore = FloatingButtonPositionStore()
    @State private var showWeightOnlyAlert = false
    @State private var isDragging = false
    @State private var dragOffset = CGSize.zero
    @State private var currentPosition = CGPoint.zero

    // Constants for positioning
    private let buttonSize: CGFloat = 80
    private let defaultOpacity: Double = 0.5
    private let activeOpacity: Double = 1.0
    private let animationDuration: Double = 0.3

    var body: some View {
        GeometryReader { geometry in
            Button(action: {
                if !isDragging {
                    showWeightOnlyAlert = true
                }
            }) {
                Image(AppAssets.weightOnlyModeAlertIcon)
                    .resizable()
                    .scaledToFit()
                    .frame(width: buttonSize, height: buttonSize)
                    .background(
                        Circle()
                            .fill(theme.backgroundPrimary)
                            .shadow(
                              color: theme.logoPrimary.opacity(0.3),
                                radius: 8,
                                x: 0,
                                y: 4
                            )
                    )
            }
            .buttonStyle(PlainButtonStyle())
            .opacity(isDragging ? activeOpacity : defaultOpacity)
            .animation(.easeInOut(duration: animationDuration), value: isDragging)
            .position(
                x: currentPosition.x + dragOffset.width,
                y: currentPosition.y + dragOffset.height
            )
            .animation(.easeInOut(duration: animationDuration), value: currentPosition)
            .gesture(
                DragGesture()
                    .onChanged { value in
                        if !isDragging {
                            isDragging = true
                        }
                        dragOffset = value.translation
                    }
                    .onEnded { value in
                        isDragging = false
                        snapToSide(in: geometry, dragValue: value)
                        dragOffset = .zero
                    }
            )
            .onAppear {
                loadInitialPosition(in: geometry)
            }
        }
        .allowsHitTesting(true)
        .sheet(isPresented: $showWeightOnlyAlert) {
            WeightOnlyModeAlertView()
        }
    }

    // MARK: - Position Management

    private func loadInitialPosition(in geometry: GeometryProxy) {
        let savedPosition = positionStore.getSavedPosition()

        if let saved = savedPosition {
            currentPosition = validatePosition(saved, in: geometry)
        } else {
            // Default position: bottom-right corner
            currentPosition = getDefaultPosition(in: geometry)
        }
    }

    private func snapToSide(in geometry: GeometryProxy, dragValue: DragGesture.Value) {
        let finalPosition = CGPoint(
            x: currentPosition.x + dragValue.translation.width,
            y: currentPosition.y + dragValue.translation.height
        )

        let snappedPosition = calculateSnapPosition(finalPosition, in: geometry)
        currentPosition = snappedPosition

        // Save the new position
        positionStore.savePosition(snappedPosition)
    }

    private func calculateSnapPosition(_ position: CGPoint, in geometry: GeometryProxy) -> CGPoint {
        let safeArea = geometry.safeAreaInsets
        let windowWidth = geometry.size.width
        let windowHeight = geometry.size.height

        // Determine which side to snap to
        let isLeftSide = position.x < windowWidth / 2
        let isTopSide = position.y < windowHeight / 2

        // Calculate offsets based on screen size
        let topOffset: CGFloat = windowWidth > 500 ? 85 : 65
        let bottomOffset: CGFloat = windowWidth > 500 ? 120 : 90
        let leftOffset: CGFloat = windowWidth > 500 ? 20 : 8
        let rightOffset: CGFloat = windowWidth > 500 ? 120 : 90

        // Calculate snap position
        let snapX = isLeftSide ?
            leftOffset + buttonSize / 2 :
            windowWidth - rightOffset + buttonSize / 2

        let snapY = isTopSide ?
            safeArea.top + topOffset + buttonSize / 2 :
            windowHeight - safeArea.bottom - bottomOffset - buttonSize / 2

        return CGPoint(x: snapX, y: snapY)
    }

    private func getDefaultPosition(in geometry: GeometryProxy) -> CGPoint {
        let safeArea = geometry.safeAreaInsets
        let windowWidth = geometry.size.width
        let windowHeight = geometry.size.height

        // Default to bottom-right
        return CGPoint(
            x: windowWidth - 90,
            y: windowHeight - safeArea.bottom - 90
        )
    }

    private func validatePosition(_ position: CGPoint, in geometry: GeometryProxy) -> CGPoint {
        let safeArea = geometry.safeAreaInsets
        let windowWidth = geometry.size.width
        let windowHeight = geometry.size.height

        let minX = buttonSize / 2
        let maxX = windowWidth - buttonSize / 2
        let minY = safeArea.top + buttonSize / 2
        let maxY = windowHeight - safeArea.bottom - buttonSize / 2

        return CGPoint(
            x: max(minX, min(maxX, position.x)),
            y: max(minY, min(maxY, position.y))
        )
    }
}

// MARK: - Position Storage Service

/// Service to persist the floating button position
/// Equivalent to Angular's MetricDisplayService position storage
@MainActor
class FloatingButtonPositionStore: ObservableObject {
    private let userDefaults = UserDefaults.standard
    private let topKey = "weight_only_indicator_top"
    private let leftKey = "weight_only_indicator_left"

    func savePosition(_ position: CGPoint) {
        userDefaults.set(position.x, forKey: leftKey)
        userDefaults.set(position.y, forKey: topKey)
    }

    func getSavedPosition() -> CGPoint? {
        let x = userDefaults.double(forKey: leftKey)
        let y = userDefaults.double(forKey: topKey)

        // Check if values exist (0 is a valid position)
        if userDefaults.object(forKey: leftKey) != nil && userDefaults.object(forKey: topKey) != nil {
            return CGPoint(x: x, y: y)
        }

        return nil
    }

    func clearSavedPosition() {
        userDefaults.removeObject(forKey: leftKey)
        userDefaults.removeObject(forKey: topKey)
    }
}

// MARK: - Preview

#Preview {
    ZStack {
        Color.gray.opacity(0.1)
            .ignoresSafeArea()

        WeightOnlyModeIndicator()
    }
    .environmentObject(Theme.shared)
}
