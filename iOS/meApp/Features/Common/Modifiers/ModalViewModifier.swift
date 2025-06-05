//
//  ModalViewModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import SwiftUI

/// A view modifier that presents a customizable modal overlay with optional backdrop tap-to-dismiss behavior.
///
/// Usage:
/// 1. Define your `ModalData` containing a `presentedView` to be shown as the modal.
/// 2. Optionally specify:
///    - `backdropDismiss`: whether tapping the dimmed background dismisses the modal.
///    - `onDismiss`: a closure executed after the modal is dismissed.
/// 3. Attach `.presentModal(modalViewData: $modalViewData)` to your root view.
///
/// Example:
/// ```swift
/// @State private var modalViewData: ModalData? = nil
///
/// modalViewData = ModalData(
///     presentedView: AnyView(YourCustomModalView()),
///     backdropDismiss: true,
///     onDismiss: { print("Modal closed") }
/// )
/// ```
///
/// Features:
/// - Presents any custom view as a modal overlay
/// - Tap outside to dismiss (if `backdropDismiss` is true)
/// - Optional `onDismiss` callback
/// - Smooth scale transition animation
/// - Configurable from any view that owns the state
///
/// Notes:
/// - For consistent styling, ensure `presentedView` has appropriate padding or background.
/// - You can customize the `.transition()` used inside `ModalViewModifier` as needed.

struct ModalViewModifier: ViewModifier {
    @Binding var modalStack: [ModalData]
    @Environment(\.appTheme) private var theme
    
    func body(content: Content) -> some View {
        ZStack {
            content
            
            ForEach(Array(modalStack.enumerated()), id: \.element.id) { index, modal in
                ZStack {
                    theme.backgroundPrimary.opacity(0.5)
                        .ignoresSafeArea()
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if modal.backdropDismiss {
                                modal.onDismiss?()
                                if let idx = modalStack.firstIndex(where: { $0.id == modal.id }) {
                                    modalStack.remove(at: idx)
                                }
                            }
                        }
                        .transition(.opacity)
                    
                    modal.presentedView
                        .transition(.scale)
                }
                .zIndex(Double(index))
            }
        }
        .animation(.easeInOut, value: modalStack)
    }
}

extension View {
    func presentModal(modalStack: Binding<[ModalData]>) -> some View {
        modifier(ModalViewModifier(modalStack: modalStack))
    }
}
