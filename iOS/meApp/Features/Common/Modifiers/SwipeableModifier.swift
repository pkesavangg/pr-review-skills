//
//  SwipeableModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//
import SwiftUI

// Mark: - SwipeableModifier
/// A view modifier that adds swipeable buttons to a view.
/// This modifier allows you to add a set of swipeable buttons that appear when the view is swiped left.
struct SwipeableModifier: ViewModifier {
    let swipeButtons: [SwipeButton]
    let buttonWidth: CGFloat
    let itemID: UUID
    var openItemID: Binding<UUID?>?
    
    @State private var offsetX: CGFloat = 0
    @GestureState private var dragOffset: CGFloat = 0
    
    private var isSwipedOpen: Bool {
        offsetX < 0
    }
    
    func body(content: Content) -> some View {
        ZStack(alignment: .trailing) {
            // Swipe buttons
            HStack(spacing: 0) {
                ForEach(swipeButtons) { button in
                    Button {
                        if isSwipedOpen {
                            button.action()
                        }
                    } label: {
                        button.label()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .frame(width: buttonWidth)
                    .background(button.tint)
                    .contentShape(Rectangle())
                    .allowsHitTesting(isSwipedOpen)
                }
            }
            
            content
                .background(Color.clear)
                .offset(x: offsetX + dragOffset)
                .gesture(
                    swipeButtons.isEmpty ? nil :
                    DragGesture()
                        .updating($dragOffset) { value, state, _ in
                            let maxSwipe = CGFloat(swipeButtons.count) * buttonWidth
                            let translation = min(0, value.translation.width)
                            let clamped = max(translation, -maxSwipe - 10) // Allow slight overscroll
                            state = clamped
                        }
                        .onEnded { value in
                            let totalWidth = CGFloat(swipeButtons.count) * buttonWidth
                            let threshold = -totalWidth / 2
                            withAnimation {
                                if value.translation.width < threshold {
                                    offsetX = -totalWidth
                                    openItemID?.wrappedValue = itemID // mark this row as opened (only if single-open behavior is desired)
                                } else {
                                    offsetX = 0
                                    if openItemID?.wrappedValue == itemID { openItemID?.wrappedValue = nil }
                                }
                            }
                        }
                )
        }
        .clipped()
        // Close if a different item becomes open (only in single-open mode)
        .onChange(of: openItemID?.wrappedValue) { newValue in
            guard let newValue else { return }
            if newValue != itemID && offsetX != 0 {
                withAnimation { offsetX = 0 }
            }
        }
        .onChange(of: swipeButtons.count) {
            if swipeButtons.count == 0 { offsetX = 0 }
        }
    }
}
