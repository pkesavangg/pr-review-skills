//
//  ToastModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//
import SwiftUI

/// A view modifier that presents a customizable toast notification with optional action button.
///
/// Usage:
/// 1. Define your `ToastModel` with a title, optional message, optional button view, and a click action.
/// 2. The toast can be dismissed by:
///    - Swiping left/right and releasing (animated upward dismissal)
///    - Waiting for the duration to expire (auto-dismissal)
///    - Tapping the action button (if provided)
/// 3. Attach `.presentToast(data: $toastData)` to your root view.
///
/// Example:
/// ```swift
/// @State private var toastData: ToastModel? = nil
///
/// toastData = ToastModel(
///     title: "Success",
///     message: "Your changes have been saved",
///     buttonView: AnyView(
///         Text("UNDO")
///             .foregroundColor(.blue)
///             .fontWeight(.bold)
///     ),
///     onClick: { print("Undo clicked") },
///     duration: 3.0
/// )
/// ```
///
/// Features:
/// - Supports title and optional message
/// - Optional action button with custom view
/// - Interactive swipe-to-dismiss gesture
/// - Automatic dismissal after specified duration
/// - Smooth animations for show/hide/swipe
/// - Queue management for multiple toasts
struct ToastModifier: ViewModifier {
    @Environment(\.appTheme) private var theme
    @Binding var toastData: ToastModel?

    @State private var offset = CGSize.zero
    @State private var isDragging = false
    @State private var timer: DispatchSourceTimer?
    @State private var activeToasts: [(id: UUID, toast: ToastModel)] = []
    
    func body(content: Content) -> some View {
        ZStack {
            content
            VStack {
                ZStack {
                    ForEach(activeToasts, id: \.id) { toastItem in
                        toastView(for: toastItem.toast)
                            .transition(
                                .asymmetric(
                                    insertion: AnyTransition.opacity.combined(with: .move(edge: .top))
                                        .animation(.spring(response: 0.5, dampingFraction: 0.65)),
                                    removal: AnyTransition.opacity.combined(with: .move(edge: .top))
                                        .animation(.spring(response: 0.5, dampingFraction: 0.65))
                                )
                            )
                            .zIndex(1)
                            .id(toastItem.id)
                    }
                }
                Spacer()
            }
            .onChange(of: toastData) {
                if let toast = toastData {
                    addToast(toast)
                }
            }
        }
    }
    
    private func toastView(for data: ToastModel) -> some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: .spacingXS) {
                if let title = data.title {
                    Text(title)
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }

                Text(data.message)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)

                if let buttonTextView = data.btnTextView {
                    Button {
                        data.onClick()
                        if let firstToast = activeToasts.first {
                            removeToast(id: firstToast.id)
                        }
                    } label: {
                        buttonTextView
                            .foregroundColor(theme.actionPrimary)
                            .padding(.trailing, 5)
                    }
                    .padding(.top, 4)
                }
            }
        }
        .padding(.spacingSM)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(width: min(UIScreen.main.bounds.width * 0.9, 550))
        .background(theme.supportToastBackground)
        .cornerRadius(.radiusSM)
        .padding()
        .offset(x: offset.width, y: offset.height)
        .gesture(
            DragGesture()
                .onChanged { gesture in
                    offset.width = gesture.translation.width
                    isDragging = true
                }
                .onEnded { gesture in
                    isDragging = false
                    let velocity = gesture.predictedEndLocation.x - gesture.location.x
                    
                    if abs(offset.width) > 50 || abs(velocity) > 100 {
                        withAnimation(.spring(response: 0.5, dampingFraction: 0.65)) {
                            offset.height = -200
                        }
                        
                        // Delay the removal to match the animation
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            if let firstToast = activeToasts.first {
                                removeToast(id: firstToast.id)
                            }
                        }
                    } else {
                        // Reset position if not swiped enough
                        withAnimation(.spring(response: 0.5, dampingFraction: 0.65)) {
                            offset = .zero
                        }
                    }
                }
        )
    }
    
    private func addToast(_ toast: ToastModel) {
        // Add slight delay before showing new toast if there's an existing one
        let delay = activeToasts.isEmpty ? 0.0 : 0.15
        
        if !activeToasts.isEmpty {
            // Remove existing toast first
            removeToast(id: activeToasts[0].id)
        }
        
        // Reset offset before showing new toast
        offset = .zero
        isDragging = false
        
        // Add new toast after delay
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.65)) {
                let newToastItem = (id: UUID(), toast: toast)
                activeToasts.append(newToastItem)
                
                // Setup auto-dismiss timer
                timer?.cancel()
                let newTimer = DispatchSource.makeTimerSource()
                newTimer.schedule(deadline: .now() + toast.duration)
                newTimer.setEventHandler {
                    DispatchQueue.main.async {
                        removeToast(id: newToastItem.id)
                    }
                }
                newTimer.resume()
                timer = newTimer
            }
        }
        
        // Clear the binding
        toastData = nil
    }
    
    private func removeToast(id: UUID) {
        if let toast = activeToasts.first(where: { $0.id == id })?.toast {
            toast.onDismiss?()
        }
        withAnimation(.spring(response: 0.5, dampingFraction: 0.65)) {
            activeToasts.removeAll { $0.id == id }
            // Reset offset and dragging state after toast is removed
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                offset = .zero
                isDragging = false
            }
        }
        if id == activeToasts.first?.id {
            timer?.cancel()
            timer = nil
        }
    }
}
