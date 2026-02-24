//
//  SwipeableModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//
import SwiftUI

// MARK: - SwipeState
/// The swipe gesture's current state
enum SwipeState {
    case closed
    case expanded
    case triggering
}

// MARK: - GestureVelocity
/// Property wrapper to track gesture velocity
@propertyWrapper
struct GestureVelocity: DynamicProperty {
    @State var previous: DragGesture.Value?
    @State var current: DragGesture.Value?

    func update(_ value: DragGesture.Value) {
        if current != nil {
            previous = current
        }
        current = value
    }

    func reset() {
        previous = nil
        current = nil
    }

    var projectedValue: GestureVelocity {
        return self
    }

    var wrappedValue: CGVector {
        value
    }

    private var value: CGVector {
        guard let previous, let current else {
            return .zero
        }

        let timeDelta = current.time.timeIntervalSince(previous.time)
        let speedY = Double(current.translation.height - previous.translation.height) / timeDelta
        let speedX = Double(current.translation.width - previous.translation.width) / timeDelta

        return .init(dx: speedX, dy: speedY)
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

    // MARK: - Configuration
    private let swipeMinimumDistance: CGFloat = 20 // Increased to avoid conflicts with scrolling
    private let expandThreshold: CGFloat = 50
    private let rubberBandPower: CGFloat = 0.7
    private let animationDuration: CGFloat = 0.3
    private let velocityThreshold: CGFloat = 200
    private let maxSwipeAngle: CGFloat = 30 // Reduced angle for stricter horizontal detection

    // MARK: - State
    @State private var currentOffset: CGFloat = 0
    @State private var savedOffset: CGFloat = 0
    @State private var swipeState: SwipeState = .closed
    @State private var isSwipedOpen: Bool = false
    @GestureVelocity private var velocity: CGVector
    @GestureState private var isDragging: Bool = false
    @State private var dragStarted: Bool = false
    @State private var isHorizontalSwipe: Bool = false
    @State private var gestureStartPoint: CGPoint = .zero

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
        totalButtonWidth + 20 // Small padding for overscroll
    }

// swiftlint:disable:next function_body_length
    func body(content: Content) -> some View {
        let dragGesture = DragGesture(minimumDistance: swipeMinimumDistance)
            .updating($isDragging) { _, state, _ in
                state = true
            }
            .onChanged(onDragChanged)
            .onEnded(onDragEnded)
            .updatingVelocity($velocity)

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
// swiftlint:disable:next multiple_closures_with_trailing_closure
                    }) {
                        button.label()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .frame(width: buttonWidth)
                    .background(button.tint)
                    .contentShape(Rectangle())
                    .allowsHitTesting(isSwipedOpen)
                }
            }
            .frame(width: totalButtonWidth)
            .opacity(swipeState == .closed ? 0 : 1)

            // Main content
            content
                .background(Color.clear)
                .offset(x: totalOffset)
                .animation(.interactiveSpring(response: animationDuration, dampingFraction: 0.8), value: totalOffset)
        }
        .clipped()
        .gesture(
            dragGesture,
            including: swipeButtons.isEmpty ? .subviews : .all
        )
        .onChange(of: isDragging) { _, newValue in
            if newValue {
                dragStarted = true
                // Mark this item as potentially opening only if it's a horizontal swipe
                if swipeState == .closed && isHorizontalSwipe {
                    openItemID?.wrappedValue = itemID
                }
            } else {
                dragStarted = false
                isHorizontalSwipe = false
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
    private func onDragChanged(_ value: DragGesture.Value) {
        $velocity.update(value)

        // Calculate swipe angle to determine if it's horizontal
        let deltaX = value.location.x - value.startLocation.x
        let deltaY = value.location.y - value.startLocation.y
        let angle = abs(atan2(deltaY, deltaX) * 180 / .pi)

        // Check if this is a horizontal swipe
        if !isHorizontalSwipe && abs(deltaX) > swipeMinimumDistance {
            let isHorizontal = angle <= maxSwipeAngle || angle >= (180 - maxSwipeAngle)
            if isHorizontal {
                isHorizontalSwipe = true
            }
        }

        // Only process horizontal swipes - this prevents interference with vertical scroll
        guard isHorizontalSwipe else { return }

        // Additional check: ensure horizontal movement is dominant
        // Use a stricter threshold to be more selective about horizontal vs vertical detection
        let horizontalThreshold: CGFloat = 2.0 // Increased from 1.5 to 2.0 for stricter detection
        if abs(deltaY) > abs(deltaX) * horizontalThreshold {
            // Vertical movement is dominant, don't process
            return
        }
        
        // Additional check: require minimum horizontal movement before processing
        if abs(deltaX) < swipeMinimumDistance {
            return
        }

        let translation = value.translation.width
        let totalTranslation = savedOffset + translation

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

    private func onDragEnded(_ value: DragGesture.Value) {
        let translation = value.translation.width
        let totalTranslation = savedOffset + translation
        let gestureVelocity = $velocity.wrappedValue.dx

        // Reset horizontal swipe flag
        defer { isHorizontalSwipe = false }

        // If this wasn't a horizontal swipe, don't process the end
        guard isHorizontalSwipe else {
            $velocity.reset()
            return
        }

        // Don't allow positive swipes
        if totalTranslation > 0 {
            closeSwipe()
            return
        }

        // Determine final state based on position and velocity
        let absOffset = abs(totalTranslation)
        _ = abs(gestureVelocity) > velocityThreshold
        let velocityTowardsOpen = gestureVelocity < -velocityThreshold
        let velocityTowardsClose = gestureVelocity > velocityThreshold

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

        $velocity.reset()
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

// MARK: - Gesture Extensions
extension Gesture where Value == DragGesture.Value {
    func updatingVelocity(_ velocity: GestureVelocity) -> _EndedGesture<_ChangedGesture<Self>> {
        onChanged { value in
            velocity.update(value)
        }
        .onEnded { _ in
            velocity.reset()
        }
    }
}
