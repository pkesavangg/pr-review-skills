//
//  NotificationHelperService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//
import Foundation

@MainActor
class NotificationHelperService: ObservableObject {
    public static let shared = NotificationHelperService()
    @Published var alertData: AlertModel? = nil
    @Published var toastData: ToastModel? = nil
    @Published var loaderData: LoaderModel? = nil
    @Published var modalViewData: [ModalData] = []
    @Published var isOverlayActive: Bool = false
    
    /// property to track if any toasts are active in the modifier
    @Published private var hasActiveToasts: Bool = false
    
    var isAlertVisible: Bool {
        alertData != nil
    }
    
    var isToastVisible: Bool {
        hasActiveToasts
    }
    
    var isLoaderVisible: Bool {
        loaderData != nil
    }
    
    var isModalVisible: Bool {
        modalViewData.count > 0
    }
    
    func showAlert(_ alert: AlertModel) {
        let wrappedButtons = alert.buttons.map { button in
            AlertButtonModel(
                title: button.title,
                type: button.type
            ) { value in
                button.action(value)
                self.dismissAlert()
            }
        }
        
        let wrappedAlert = AlertModel(
            title: alert.title,
            message: alert.message,
            buttons: wrappedButtons,
            inputField: alert.inputField
        )
        
        DispatchQueue.main.async {
            self.alertData = wrappedAlert
            self.isOverlayActive = true
        }
    }
    
    func dismissAlert() {
        DispatchQueue.main.async {
            self.alertData = nil
            self.updateOverlayState()
        }
    }
    
    func showToast(_ data: ToastModel) {
        var toast = data
        toast.onDismiss = { [weak self] in
            self?.dismissToast()
        }
        /// Set up the active count change handler to update hasActiveToasts
        toast.onActiveCountChanged = { [weak self] count in
            DispatchQueue.main.async {
                self?.hasActiveToasts = count > 0
            }
        }
        
        DispatchQueue.main.async {
            self.toastData = toast
            self.hasActiveToasts = true
        }
    }
    
    func dismissToast() {
        DispatchQueue.main.async {
            self.toastData = nil
            // Don't immediately set hasActiveToasts to false - let ToastModifier manage this
            self.updateOverlayState()
        }
    }
    
    func showLoader(_ loader: LoaderModel) {
        DispatchQueue.main.async {
            self.loaderData = loader
            self.isOverlayActive = true
        }
    }
    
    func dismissLoader() {
        DispatchQueue.main.async {
            self.loaderData = nil
            self.updateOverlayState()
        }
    }
    
    func dismissAllNotifications() {
        DispatchQueue.main.async {
            self.alertData = nil
            self.toastData = nil
            self.loaderData = nil
            self.modalViewData = []
            self.isOverlayActive = false
        }
    }
    
    func showModal(_ modal: ModalData) {
        let wrappedModal = ModalData(
            presentedView: modal.presentedView,
            backdropDismiss: modal.backdropDismiss,
            onDismiss: {
                modal.onDismiss?()
                self.dismissModal()
            }
        )
        DispatchQueue.main.async {
            self.modalViewData.append(wrappedModal)
            self.isOverlayActive = true
        }
    }
    
    
    func dismissModal() {
        DispatchQueue.main.async {
            if !self.modalViewData.isEmpty {
                self.modalViewData.removeLast()
            }
            self.updateOverlayState()
        }
    }
    
    func dismissAllModals() {
        DispatchQueue.main.async {
            self.modalViewData = []
            self.isOverlayActive = false
        }
    }
    
    /// Updates overlay state based on active notifications
    /// Note: Toasts don't block the main window, so they're excluded from overlay state
    private func updateOverlayState() {
        isOverlayActive = isAlertVisible || isLoaderVisible || isModalVisible
    }
}

/// NotificationHelperService is a MainActor class that manages all notification states and operations.
/// It provides a centralized way to show/dismiss different types of notifications:
/// - Alerts: For important user decisions/confirmations
/// - Toasts: For temporary informational messages
/// - Loaders: For loading states
/// - Modals: For modal views
///
/// Usage Example:
/// ```swift
/// class SomeViewModel {
///     @Injector var notificationService: NotificationHelperService
///
///     func showSampleAlert() {
///         let alert = AlertModel(
///             title: "Confirm Action",
///             message: "Are you sure?",
///             buttons: [
///                 AlertButtonModel(title: "Yes", type: .primary) { _ in
///                     print("Confirmed")
///                 },
///                 AlertButtonModel(title: "Cancel", type: .secondary) { _ in
///                     print("Cancelled")
///                 }
///             ]
///         )
///         notificationService.showAlert(alert)
///     }
///
///     func showSampleToast() {
///         let toast = ToastModel(
///             title: "Success",
///             message: "Operation completed",
///             duration: 3
///         )
///         notificationService.showToast(toast)
///     }
///
///     func showSampleLoader() {
///         notificationService.showLoader(LoaderModel(text: "Loading..."))
///         // Later:
///         notificationService.dismissLoader()
///     }
///
///     func showSampleModal() {
///         let modal = ModalData(
///             presentedView: AnyView(YourModalView()),
///             backdropDismiss: true,
///             onDismiss: {
///                 print("Modal dismissed")
///             }
///         )
///         notificationService.showModal(modal)
///     }
/// }
/// ```
///
/// Key Features:
/// 1. Thread-safe: All operations are performed on the main thread via @MainActor
/// 2. State Management: Tracks visibility state of all notification types
/// 3. Multiple Modals: Supports stacking of multiple modal views
/// 4. Automatic Cleanup: Handles proper cleanup of notifications
/// 5. Theme Support: Integrates with the app's theming system
