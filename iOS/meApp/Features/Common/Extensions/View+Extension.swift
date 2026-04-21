//
//  View+Extension.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import Combine
import SwiftUI

extension View {
    /// Presents an alert with the provided alert data.
    /// - Parameter alertData: A binding to the `AlertModel?` that contains the alert configuration.
    /// - Returns: A view that presents the alert when `alertData` is not nil.
    func presentAlert(alertData: Binding<AlertModel?>) -> some View {
        self.modifier(AlertModifier(alertData: alertData))
    }
    
    /// Conditional modifier that applies a transformation to the view if the condition is true.
    /// - Parameters:
    ///   - condition: A Boolean value that determines whether the transformation should be applied.
    ///   - transform: A closure that takes the current view and returns a transformed view.
    /// - Returns: A view that conditionally applies the transformation based on the condition.
    @ViewBuilder
    func `if`<Transform: View>(_ condition: Bool, transform: (Self) -> Transform) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }

    @ViewBuilder
    func ifLet<T, Transform: View>(_ value: T?, transform: (Self, T) -> Transform) -> some View {
        if let value {
            transform(self, value)
        } else {
            self
        }
    }
    
    /// Presents a toast notification with the provided toast data.
    /// - Parameters:
    ///   - data: A binding to the `ToastModel?` that contains the toast configuration.
    ///   - dismissSignal: An optional publisher that triggers immediate toast removal.
    /// - Returns: A view that presents the toast when `data` is not nil.
    func presentToast(data: Binding<ToastModel?>, dismissSignal: AnyPublisher<Void, Never>? = nil) -> some View {
        self.modifier(ToastModifier(toastData: data, dismissSignal: dismissSignal))
    }
    
    /// Presents a loader with the provided loader data.
    /// - Parameter loaderData: A binding to the `LoaderModel?` that contains the loader configuration.
    /// - Returns: A view that presents the loader when `loaderData` is not nil.
    func presentLoader(loaderData: Binding<LoaderModel?>) -> some View {
        self.modifier(LoaderModifier(loaderData: loaderData))
    }
    
    /// Presents a modal view with the provided modal view data.
    /// - Parameter modalViewData: A binding to an array of `ModalData` that contains the modal configuration.
    /// - Returns: A view that presents the modal when `modalViewData` is not empty.
    func presentModal(modalViewData: Binding<[ModalData]>) -> some View {
        self.modifier(ModalViewModifier(modalStack: modalViewData))
    }
    
    /// Presents a picker sheet with the provided configuration.
    /// - Parameters:
    /// - isPresented: A binding to a Boolean value that determines whether the picker sheet is presented.
    /// - selectedValues: An array of selected values of type `T`.
    /// - options: A 2D array of options of type `T` to choose from.
    /// - displayValue: A closure that takes a value of type `T` and returns a string to display.
    /// - pickerType: The type of picker to display (time, heightInches, heightCm).
    /// - onUpdate: A closure that is called when the selected values are updated.
    /// - Returns: A view that presents the picker sheet when `isPresented` is true.
    func pickerSheet<T: Hashable>(
        isPresented: Binding<Bool>,
        selectedValues: [T],
        options: [[T]],
        displayValue: @escaping (T) -> String,
        pickerType: PickerType = .default,
        allowTapOutside: Bool = true,
        title: String? = nil,
        showCancel: Bool = false,
        onUpdate: @escaping ([T]) -> Void
    ) -> some View {
        modifier(
            PickerSheetModifier(
                isPresented: isPresented,
                selectedValues: selectedValues,
                options: options,
                displayValue: displayValue,
                pickerType: pickerType,
                onUpdate: onUpdate,
                title: title,
                showCancel: showCancel,
                allowTapOutside: allowTapOutside
            )
        )
    }
    
    /// Hides the keyboard by resigning the first responder status.
    func hideKeyboard() {
        let resign = #selector(UIResponder.resignFirstResponder)
        UIApplication.shared.sendAction(resign, to: nil, from: nil, for: nil)
    }
    
    /// Dismisses the keyboard when the view is dragged.
    func dismissKeyboardOnDrag() -> some View {
        self
            .contentShape(Rectangle()) // Ensures transparent areas can receive gestures
            .highPriorityGesture(
                DragGesture().onChanged { _ in
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
            )
    }
    
    /// Applies a border to the view with customizable sides, thickness, and color.
    /// - Parameters:
    ///   - sides: An array of sides where the border should be applied.
    ///   - thickness: The thickness of the border.
    ///   - color: The color of the border. If nil, the default color will be used.
    ///   - Returns: A view with a border applied to the specified sides.
    func border(
        sides: [BorderModifier.Side] = [.top, .bottom, .leading, .trailing],
        thickness: CGFloat = 1,
        color: Color? = nil
    ) -> some View {
        modifier(BorderModifier(sides: sides, thickness: thickness, color: color))
    }
    
    /// Applies a modifier to set the row insets for settings views.
    /// - Parameters:
    ///   - top: Top inset (default is 11).
    ///   - bottom: Bottom inset (default is 11).
    ///   - leading: Leading inset (default is .spacingSM).
    ///   - trailing: Trailing inset (default is .spacingSM).
    /// - Returns: A view with modified row insets suitable for settings.
    func listRowInsets(
        top: CGFloat = 0,
        bottom: CGFloat = 0,
        leading: CGFloat = .spacingSM,
        trailing: CGFloat = .spacingSM
    ) -> some View {
        self.modifier(
            ListRowInsetModifier(
                top: top,
                bottom: bottom,
                leading: leading,
                trailing: trailing
            )
        )
    }
    
    /// Applies a swipeable modifier to the view with customizable button width and buttons.
    /// - Parameters:
    ///  - buttonWidth: The width of each swipeable button (default is 72).
    ///  - buttons: An array of `SwipeButton` configurations for the swipeable actions.
    ///  - itemID: The unique identifier for the row.
    ///  - openItemID: A binding to the currently opened row identifier to ensure only one row is open at a time.
    ///  - Returns: A view with swipeable actions applied.
    func swipeableActions(
        buttonWidth: CGFloat = 72,
        buttons: [SwipeButton],
        itemID: UUID,
        openItemID: Binding<UUID?>? = nil,
        openThresholdFraction: CGFloat = 0.5,
        closeWithoutAnimationOnAction: Bool = false,
        trailingCornerRadius: CGFloat = 0
    ) -> some View {
        modifier(
            SwipeableModifier(
                swipeButtons: buttons,
                buttonWidth: buttonWidth,
                itemID: itemID,
                openItemID: openItemID,
                openThresholdFraction: openThresholdFraction,
                closeWithoutAnimationOnAction: closeWithoutAnimationOnAction,
                trailingCornerRadius: trailingCornerRadius
            )
        )
    }
  
    /// Applies a conditional wiggle animation to the view.
    /// - Parameter shouldWiggle: If true, applies the wiggle animation.
    /// - Returns: A view that conditionally wiggles based on the parameter.
    func wiggling(_ shouldWiggle: Bool) -> some View {
        modifier(WiggleModifier(shouldWiggle: shouldWiggle))
    }
    
    /// Applies a conditional wiggle animation to the view with row-based timing.
    /// - Parameters:
    ///   - shouldWiggle: If true, applies the wiggle animation.
    ///   - rowIndex: The row index to determine alternating animation timing.
    /// - Returns: A view that conditionally wiggles with row-based timing.
    func wiggling(_ shouldWiggle: Bool, rowIndex: Int) -> some View {
        modifier(WiggleModifier(shouldWiggle: shouldWiggle, rowIndex: rowIndex))
    }
    
    /// Applies a conditional wiggle animation to the view with custom parameters.
    /// - Parameters:
    ///   - shouldWiggle: If true, applies the wiggle animation.
    ///   - rowIndex: The row index to determine alternating animation timing.
    ///   - evenRowDuration: Custom duration for even rows (optional).
    ///   - oddRowDuration: Custom duration for odd rows (optional).
    ///   - wiggleAngle: Custom rotation angle in degrees (optional).
    /// - Returns: A view that conditionally wiggles with custom parameters.
    func wiggling(
        _ shouldWiggle: Bool,
        rowIndex: Int = 0,
        evenRowDuration: Double? = nil,
        oddRowDuration: Double? = nil,
        wiggleAngle: Double? = nil
    ) -> some View {
        modifier(WiggleModifier(
            shouldWiggle: shouldWiggle,
            rowIndex: rowIndex,
            evenRowDuration: evenRowDuration,
            oddRowDuration: oddRowDuration,
            wiggleAngle: wiggleAngle
        ))
    }
    
    /// Applies a widget-style wiggle animation to the view (slower, gentler).
    /// - Parameters:
    ///   - shouldWiggle: If true, applies the widget wiggle animation.
    ///   - rowIndex: The row index for alternating timing (even/odd rows).
    /// - Returns: A view that conditionally wiggles with widget-style animation.
    func widgetWiggling(_ shouldWiggle: Bool, rowIndex: Int = 0) -> some View {
        modifier(WiggleModifier(
            shouldWiggle: shouldWiggle,
            rowIndex: rowIndex,
            evenRowDuration: 0.35,
            oddRowDuration: 0.33,
            wiggleAngle: 0.045
        ))
    }
    
    /// Applies a medium-speed wiggle animation to the view (faster than widget, gentler than app icon).
    /// - Parameters:
    ///   - shouldWiggle: If true, applies the medium wiggle animation.
    ///   - rowIndex: The row index for alternating timing (even/odd rows).
    /// - Returns: A view that conditionally wiggles with medium-speed animation.
    func mediumWiggling(_ shouldWiggle: Bool, rowIndex: Int = 0) -> some View {
        modifier(WiggleModifier(
            shouldWiggle: shouldWiggle,
            rowIndex: rowIndex,
            evenRowDuration: 0.18,
            oddRowDuration: 0.16,
            wiggleAngle: 0.045
        ))
    }
    
    /// Applies edit mode overlay with plus/minus circles and row-based wiggle timing
    /// - Parameters:
    ///   - isEditMode: Whether edit mode is active
    ///   - isRemoved: Whether the item is removed
    ///   - onToggleRemoval: Callback for toggling removal
    ///   - isBeingDragged: Whether the item is being dragged
    ///   - isDropTarget: Whether the item is a drop target
    ///   - rowIndex: Row index for alternating wiggle timing (matching movingGridsLearning exactly)
    ///   - disableWiggle: Whether to disable the internal wiggle animation (for custom wiggle animations)
    /// - Returns: A view with edit mode overlay applied
    func editModeOverlay(
        isEditMode: Bool,
        isRemoved: Bool,
        onToggleRemoval: @escaping () -> Void,
        isBeingDragged: Bool = false,
        isDropTarget: Bool = false,
        rowIndex: Int = 0,
        disableWiggle: Bool = false,
        iconOffset: CGSize = CGSize(width: 20, height: -26),
        dimWhenRemoved: Bool = true
    ) -> some View {
        modifier(EditModeOverlay(
            isEditMode: isEditMode,
            isRemoved: isRemoved,
            onToggleRemoval: onToggleRemoval,
            isBeingDragged: isBeingDragged,
            isDropTarget: isDropTarget,
            rowIndex: rowIndex,
            disableWiggle: disableWiggle,
            iconOffset: iconOffset,
            dimWhenRemoved: dimWhenRemoved
        ))
    }
    
    func draggableReorder<T: Identifiable & Equatable>(
        item: T,
        draggingItem: Binding<T?>,
        items: Binding<[T]>,
        isDraggable: Bool = true,
        onDropTargetChanged: @escaping (Bool) -> Void = { _ in },
        onDragEnd: (() -> Void)? = nil
    ) -> some View {
        if isDraggable {
            return AnyView(
                self
                    .onDrag {
                        // Set the dragging item immediately for visual feedback
                        draggingItem.wrappedValue = item
                        
                        // Create item provider with item ID for proper drag tracking
                        return NSItemProvider(object: String(describing: item.id) as NSString)
                    }
                    .onDrop(
                        of: [.text],
                        delegate: ReorderDropDelegate(
                            item: item,
                            items: items,
                            draggingItem: draggingItem,
                            onDropTargetChanged: { isTargeted in
                                onDropTargetChanged(isTargeted)
                            },
                            onDragEnd: {
                                // Ensure immediate state reset
                                Task { @MainActor in
                                    onDragEnd?()
                                }
                            }
                        )
                    )
            )
        } else {
            return AnyView(self)
        }
    }
    
    func deviceDiscoverSheetStyle(height: CGFloat = 400) -> some View {
        self.modifier(DeviceDiscoverSheetModifier(height: height))
    }
    
    /// Attaches a keyboard observer to the view and provides keyboard height as a binding
    /// - Parameter keyboardHeight: A binding to update with the current keyboard height
    /// - Returns: A view that observes keyboard events
    func keyboardObserver(keyboardHeight: Binding<CGFloat>) -> some View {
        self.modifier(KeyboardObserverModifier(keyboardHeight: keyboardHeight))
    }
    
    /// Applies a long press gesture with conditional execution based on edit mode
    /// - Parameters:
    ///   - isEditMode: Whether the view is in edit mode
    ///   - onLongPress: The action to perform on long press
    /// - Returns: A view with long press gesture applied
    func longPressGesture(isEditMode: Bool, onLongPress: @escaping () -> Void) -> some View {
        self.onLongPressGesture(minimumDuration: 0.5, maximumDistance: 50) {
            // Always execute long press action, not just in edit mode
            // This allows metric info sheets to be opened by long pressing on metric items
            onLongPress()
        }
    }
}
