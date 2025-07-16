//
//  View+Extension.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

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
    
    /// Presents a toast notification with the provided toast data.
    /// - Parameter data: A binding to the `ToastModel?` that contains the toast configuration.
    /// - Returns: A view that presents the toast when `data` is not nil.
    func presentToast(data: Binding<ToastModel?>) -> some View {
        self.modifier(ToastModifier(toastData: data))
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
    
    /// Applies a basic button style with the specified foreground color.
    /// - Parameter foregroundColor: The color to use for the button's text.
    /// - Returns: A view styled as a basic button.
    func basicButtonStyle(foregroundColor: Color) -> some View {
        self.modifier(BasicButtonStyle(foreGroundColor: foregroundColor))
    }
    
    /// Applies a bordered button style with customizable background, border color, corner radius, and size.
    /// - Parameters:
    ///   - backgroundColor: The background color of the button.
    ///   - borderColor: The color of the button's border.
    ///   - cornerRadius: The corner radius of the button.
    ///   - buttonSize: The size of the button (regular or small).
    /// - Returns: A view styled as a bordered button.
    func borderedButtonStyle(backgroundColor: Color, borderColor: Color, buttonSize: ButtonSize) -> some View {
        self.modifier(BorderedButtonStyle(backgroundColor: backgroundColor, borderColor: borderColor,buttonSize: buttonSize))
    }
    
    /// Applies a flat button style with customizable background, corner radius, and size.
    /// - Parameters:
    ///   - backgroundColor: The background color of the button.
    ///   - cornerRadius: The corner radius of the button.
    ///   - buttonSize: The size of the button (regular or small).
    /// - Returns: A view styled as a flat button.
    func flatButtonStyle(
            foregroundColor: Color,
            backgroundColor: Color,
            buttonSize: ButtonSize
        ) -> some View {
            self.modifier(FlatButtonStyle(
                foregroundColor: foregroundColor,
                backgroundColor: backgroundColor,
                buttonSize: buttonSize
            ))
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
        openItemID: Binding<UUID?>? = nil
    ) -> some View {
        modifier(
            SwipeableModifier(
                swipeButtons: buttons,
                buttonWidth: buttonWidth,
                itemID: itemID,
                openItemID: openItemID
            )
        )
    }
  
    
    /// Applies a conditional wiggle animation to the view.
    /// - Parameter shouldWiggle: If true, applies the wiggle animation.
    /// - Returns: A view that conditionally wiggles based on the parameter.
    func wiggling(_ shouldWiggle: Bool) -> some View {
        modifier(WiggleModifier(shouldWiggle: shouldWiggle))
    }
    
    /// Applies edit mode overlay with plus/minus circles
    func editModeOverlay(
        isEditMode: Bool,
        isRemoved: Bool,
        onToggleRemoval: @escaping () -> Void,
        isBeingDragged: Bool = false,
        isDropTarget: Bool = false
    ) -> some View {
        modifier(EditModeOverlay(
            isEditMode: isEditMode,
            isRemoved: isRemoved,
            onToggleRemoval: onToggleRemoval,
            isBeingDragged: isBeingDragged,
            isDropTarget: isDropTarget
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
                                DispatchQueue.main.async {
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
    
    func deviceDiscoverSheetStyle() -> some View {
        self.modifier(DeviceDiscoverSheetModifier())
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
