//
//  SwipeableModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//
import SwiftUI
import UIKit

// MARK: - SwipeState
/// The swipe gesture's current state
enum SwipeState {
    case closed
    case expanded
    case triggering
}

// MARK: - HorizontalPanGesture
/// UIKit-backed pan that only begins when the drag starts horizontally.
/// A SwiftUI `DragGesture` cannot fail based on direction — since iOS 18 it claims
/// the touch outright and blocks the enclosing ScrollView's vertical pan
/// (FB14205678), which froze scrolling on every screen with swipeable rows.
/// Rejecting vertical drags in `gestureRecognizerShouldBegin` means the swipe
/// never competes with scrolling — the same arbitration UIKit's own
/// table-view swipe actions rely on.
private struct HorizontalPanGesture: UIGestureRecognizerRepresentable {
    let isEnabled: Bool
    let onBegan: () -> Void
    /// Receives the pan translation.
    let onChanged: (CGPoint) -> Void
    /// Receives the final translation and velocity (pt/s).
    let onEnded: (CGPoint, CGPoint) -> Void

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator {
        Coordinator()
    }

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let recognizer = UIPanGestureRecognizer()
        recognizer.maximumNumberOfTouches = 1
        recognizer.delegate = context.coordinator
        return recognizer
    }

    func updateUIGestureRecognizer(_ recognizer: UIPanGestureRecognizer, context: Context) {
        recognizer.isEnabled = isEnabled
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIPanGestureRecognizer, context: Context) {
        let translation = recognizer.translation(in: recognizer.view)
        switch recognizer.state {
        case .began:
            onBegan()
        case .changed:
            onChanged(translation)
        case .ended, .cancelled, .failed:
            onEnded(translation, recognizer.velocity(in: recognizer.view))
        default:
            break
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let pan = gestureRecognizer as? UIPanGestureRecognizer,
                  let view = pan.view else { return false }
            let velocity = pan.velocity(in: view)
            return abs(velocity.x) > abs(velocity.y)
        }
    }
}

// MARK: - SwipeableModifier
/// A view modifier that adds swipeable buttons to a view with proper list scroll support
struct SwipeableModifier: ViewModifier {
    let swipeButtons: [SwipeButton]
    let buttonWidth: CGFloat
    let itemID: UUID
    var openItemID: Binding<UUID?>?
    let openThresholdFraction: CGFloat
    let closeWithoutAnimationOnAction: Bool
    var trailingCornerRadius: CGFloat

    // MARK: - Configuration
    private let rubberBandPower: CGFloat = 0.7
    private let animationDuration: CGFloat = 0.3
    private let velocityThreshold: CGFloat = 200

    // MARK: - State
    @State private var currentOffset: CGFloat = 0
    @State private var savedOffset: CGFloat = 0
    @State private var swipeState: SwipeState = .closed
    @State private var isSwipedOpen: Bool = false

    // MARK: - Computed Properties
    private var totalOffset: CGFloat {
        currentOffset + savedOffset
    }

    private var totalButtonWidth: CGFloat {
        CGFloat(swipeButtons.count) * buttonWidth
    }

    private var dynamicExpandThreshold: CGFloat {
        max(8, totalButtonWidth * openThresholdFraction)
    }

    private var maxSwipeDistance: CGFloat {
        totalButtonWidth
    }

    func body(content: Content) -> some View {
        ZStack(alignment: .trailing) {
            // Background swipe buttons
            HStack(spacing: 0) {
                ForEach(swipeButtons) { button in
                    Button(action: {
                        // Only trigger if fully opened
                        if isSwipedOpen {
                            if closeWithoutAnimationOnAction {
                                // Close without animation to avoid jiggle when alert appears
                                var tx = Transaction()
                                tx.disablesAnimations = true
                                withTransaction(tx) {
                                    savedOffset = 0
                                    currentOffset = 0
                                    swipeState = .closed
                                    isSwipedOpen = false
                                }
                                // Execute action immediately
                                button.action()
                            } else {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    savedOffset = 0
                                    currentOffset = 0
                                    swipeState = .closed
                                    isSwipedOpen = false
                                }
                                Task { @MainActor in
                                    try? await Task.sleep(nanoseconds: 100_000_000)
                                    button.action()
                                }
                            }
                        }
                    }, label: {
                        button.label()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    })
                    .frame(width: buttonWidth)
                    .background(button.tint)
                    .contentShape(Rectangle())
                    .allowsHitTesting(isSwipedOpen)
                }
            }
            .frame(width: totalButtonWidth, alignment: .trailing)
            .frame(minHeight: 0, maxHeight: .infinity)
            .clipShape(
                UnevenRoundedRectangle(
                    topLeadingRadius: 0,
                    bottomLeadingRadius: 0,
                    bottomTrailingRadius: trailingCornerRadius,
                    topTrailingRadius: trailingCornerRadius
                )
            )
            .opacity(swipeState == .closed ? 0 : 1)

            // Main content
            content
                .background(Color.clear)
                .offset(x: totalOffset)
                .animation(.interactiveSpring(response: animationDuration, dampingFraction: 0.8), value: totalOffset)
        }
        .clipped()
        .gesture(
            HorizontalPanGesture(
                isEnabled: !swipeButtons.isEmpty,
                onBegan: onPanBegan,
                onChanged: onPanChanged,
                onEnded: onPanEnded
            )
        )
        // The pan gesture is invisible to VoiceOver/Switch Control, and the buttons are
        // opacity(0) + hit-test-disabled while closed — expose each swipe action as a
        // custom accessibility action, matching what UIKit swipe actions provide natively.
        .accessibilityActions {
            ForEach(swipeButtons) { button in
                Button(action: button.action) { button.label() }
            }
        }
        .onChange(of: openItemID?.wrappedValue) { _, newValue in
            // Close this item if another item is opened OR if explicitly set to nil
            if swipeState != .closed {
                if newValue == nil || (newValue != nil && newValue != itemID) {
                    closeSwipe()
                }
            }
        }
        .onChange(of: swipeButtons.count) { _, _ in
            // Close if no buttons
            if swipeButtons.isEmpty {
                closeSwipe()
            }
        }
    }

    // MARK: - Gesture Handlers
    private func onPanBegan() {
        // Mark this item as the active one so any other open row closes
        if swipeState == .closed {
            openItemID?.wrappedValue = itemID
        }
    }

    private func onPanChanged(_ translation: CGPoint) {
        let totalTranslation = savedOffset + translation.x

        // Only allow left swipe (negative translation)
        if totalTranslation > 0 {
            // Apply rubber band effect for right swipe
            let rubberBandOffset = pow(totalTranslation, rubberBandPower)
            currentOffset = rubberBandOffset - savedOffset
            return
        }

        // Calculate constrained offset
        let constrainedOffset = max(totalTranslation, -maxSwipeDistance)
        currentOffset = constrainedOffset - savedOffset

        // Update state based on position
        let absOffset = abs(totalTranslation)
        if absOffset > totalButtonWidth {
            swipeState = .triggering
        } else if absOffset > dynamicExpandThreshold {
            swipeState = .expanded
        } else {
            swipeState = .closed
        }
    }

    private func onPanEnded(_ translation: CGPoint, _ velocity: CGPoint) {
        let totalTranslation = savedOffset + translation.x

        // Don't allow positive swipes
        if totalTranslation > 0 {
            closeSwipe()
            return
        }

        // Determine final state based on position and velocity
        let absOffset = abs(totalTranslation)
        let velocityTowardsOpen = velocity.x < -velocityThreshold
        let velocityTowardsClose = velocity.x > velocityThreshold

        if velocityTowardsClose {
            // Strong velocity towards closing
            closeSwipe()
        } else if velocityTowardsOpen && absOffset > dynamicExpandThreshold / 2 {
            // Strong velocity towards opening with minimum distance
            openSwipe()
        } else if absOffset > totalButtonWidth * openThresholdFraction {
            // Crossed configured fraction of total width point
            openSwipe()
        } else {
            // Default to close
            closeSwipe()
        }
    }

    // MARK: - State Management
    private func openSwipe() {
        withAnimation(.interactiveSpring(response: animationDuration, dampingFraction: 0.8)) {
            savedOffset = -totalButtonWidth
            currentOffset = 0
            swipeState = .expanded
            isSwipedOpen = true
        }
        openItemID?.wrappedValue = itemID
    }

    private func closeSwipe() {
        withAnimation(.interactiveSpring(response: animationDuration, dampingFraction: 0.8)) {
            savedOffset = 0
            currentOffset = 0
            swipeState = .closed
            isSwipedOpen = false
        }

        if openItemID?.wrappedValue == itemID {
            openItemID?.wrappedValue = nil
        }
    }
}
