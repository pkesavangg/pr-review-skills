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
    /// Notification service for presenting modals
    @StateObject private var notificationService = NotificationHelperService.shared
    @Environment(\.appTheme) private var theme
    @StateObject private var positionStore = FloatingButtonPositionStore()
    @State private var showWeightOnlyAlert = false
    @State private var isDragging = false
    @State private var isPressed = false
    @State private var dragOffset = CGSize.zero
    @State private var currentPosition = CGPoint.zero

    // Constants for positioning
    private let buttonSize: CGFloat = 80
    private let defaultOpacity: Double = 0.5
    private let activeOpacity: Double = 1.0
    private let animationDuration: Double = 0.3

        var body: some View {
        GeometryReader { geometry in
            // Custom draggable FAB that handles both tap and drag
            AppIconView(icon: AppAssets.weightOnlyModeAlertIcon, size: IconSize(width: buttonSize * 0.6, height: buttonSize * 0.6))
                .foregroundColor(theme.backgroundPrimary)
                .frame(width: buttonSize, height: buttonSize)
                .background(theme.actionPrimary)
                .clipShape(Circle())
                .themeDropShadow()
                .scaleEffect(isPressed ? 0.95 : 1.0)
                .opacity(isDragging ? activeOpacity : defaultOpacity)
                .animation(.easeInOut(duration: animationDuration), value: isDragging)
                .animation(.easeInOut(duration: 0.1), value: isPressed)
                .position(
                    x: currentPosition.x + dragOffset.width,
                    y: currentPosition.y + dragOffset.height
                )
                .animation(.easeInOut(duration: animationDuration), value: currentPosition)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            if !isDragging && (abs(value.translation.width) > 5 || abs(value.translation.height) > 5) {
                                isDragging = true
                            }
                            if isDragging {
                                dragOffset = value.translation
                            }
                        }
                        .onEnded { value in
                            if isDragging {
                                isDragging = false
                                snapToSide(in: geometry, dragValue: value)
                                dragOffset = .zero
                            } else {
                                // Handle tap - trigger haptic feedback and show alert
                                let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
                                impactFeedback.impactOccurred()
                                showWeightOnlyAlert = true
                            }
                        }
                )
                .simultaneousGesture(
                    // Press animation gesture
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in
                            if !isPressed {
                                withAnimation(.easeInOut(duration: 0.1)) {
                                    isPressed = true
                                }
                            }
                        }
                        .onEnded { _ in
                            withAnimation(.easeInOut(duration: 0.1)) {
                                isPressed = false
                            }
                        }
                )
                .onAppear {
                    loadInitialPosition(in: geometry)
                }
                .appAccessibility(id: AccessibilityID.weightOnlyModeIndicatorButton)
        }
        .allowsHitTesting(true)
        // Present bottom sheet: centered modal on iPad < iOS 18, sheet otherwise
        .if(DeviceUtils.useModalPicker) { view in
            view
                .onChange(of: showWeightOnlyAlert) { _, show in
                    if show {
                        let sheetView = WeightOnlyModeBottomSheet(
                            onDismiss: {
                                notificationService.dismissModal()
                                showWeightOnlyAlert = false },
                            onEnableAllBodyMetrics: {
                                notificationService.dismissModal()
                                showWeightOnlyAlert = false
                            }
                        )
                        notificationService.showModal(
                            ModalData(presentedView: AnyView(
                                sheetView
                                    .background(theme.backgroundPrimary)
                                    .cornerRadius(.radiusSM)
                            ), backdropDismiss: false)
                        )
                    }
                }
        }
        .if(!DeviceUtils.useModalPicker) { view in
            view
                .sheet(isPresented: $showWeightOnlyAlert) {
                    WeightOnlyModeBottomSheet(
                        onDismiss: { showWeightOnlyAlert = false },
                        onEnableAllBodyMetrics: { showWeightOnlyAlert = false }
                    )
                    .deviceDiscoverSheetStyle(height: 420)
                }
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
        let topOffset: CGFloat = 0
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
    private let kvStorage = KvStorageService.shared
    private let topKey = "weight_only_indicator_top"
    private let leftKey = "weight_only_indicator_left"

    func savePosition(_ position: CGPoint) {
        kvStorage.setValue(position.x, forKey: leftKey)
        kvStorage.setValue(position.y, forKey: topKey)
    }

    func getSavedPosition() -> CGPoint? {
        // Check if values exist (0 is a valid position)
        guard let xValue = kvStorage.getValue(forKey: leftKey) as? Double,
              let yValue = kvStorage.getValue(forKey: topKey) as? Double else {
            return nil
        }
        return CGPoint(x: xValue, y: yValue)
    }

    func clearSavedPosition() {
        kvStorage.clearValue(forKey: leftKey)
        kvStorage.clearValue(forKey: topKey)
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
