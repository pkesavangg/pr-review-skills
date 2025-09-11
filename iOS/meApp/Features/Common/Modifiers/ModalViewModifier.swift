//
//  ModalViewModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import SwiftUI

/// A view modifier that presents stackable modal overlays with individual backdrop tap-to-dismiss behavior.
///
/// Usage:
/// 1. Define multiple `ModalData` instances to be shown as stacked modals.
/// 2. For each modal, optionally specify:
///    - `backdropDismiss`: whether tapping that modal's dimmed background dismisses it.
///    - `onDismiss`: a closure executed after that specific modal is dismissed.
/// 3. Attach `.presentModal(modalStack: $modalStack)` to your root view.
///
/// Example:
/// ```swift
/// @State private var modalStack: [ModalData] = []
///
/// // Add first modal
/// modalStack.append(ModalData(
///     presentedView: AnyView(FirstModalView()),
///     backdropDismiss: true,
///     onDismiss: { print("First modal closed") }
/// ))
///
/// // Stack second modal on top
/// modalStack.append(ModalData(
///     presentedView: AnyView(SecondModalView()),
///     backdropDismiss: true,
///     onDismiss: { print("Second modal closed") }
/// ))
/// ```
///
/// Features:
/// - Presents multiple modals stacked on top of each other
/// - Each modal has its own backdrop and dismiss behavior
/// - Independent tap-to-dismiss for each modal's backdrop
/// - Optional onDismiss callback per modal
/// - Smooth scale and opacity transitions
/// - Proper z-index stacking for visual layering
///
/// Notes:
/// - For consistent styling, ensure each `presentedView` has appropriate padding or background.
/// - Modals are stacked in order of addition, with newer modals appearing on top.
/// - Each modal can be dismissed independently by tapping its backdrop.
/// - You can customize the `.transition()` effects used inside `ModalViewModifier` as needed.

struct ModalViewModifier: ViewModifier {
    @Binding var modalStack: [ModalData]
    @Environment(\.appTheme) private var theme
    
    func body(content: Content) -> some View {
        ZStack {
            content
            
            ForEach(Array(modalStack.enumerated()), id: \.element.id) { index, modal in
                ZStack {
                    theme.supportOverlay
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
                    .frame(width: UIScreen.main.bounds.width * 0.75)
                        .padding(.horizontal)
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
