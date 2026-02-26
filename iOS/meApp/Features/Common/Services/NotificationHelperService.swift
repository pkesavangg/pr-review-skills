//
//  NotificationHelperService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//
import Foundation

@MainActor
class NotificationHelperService: NotificationHelperServiceProtocol, ObservableObject {
    public static let shared = NotificationHelperService()
    @Published var alertData: AlertModel?
    @Published var toastData: ToastModel?
    @Published var loaderData: LoaderModel?
    @Published var modalViewData: [ModalData] = []
    @Published var isOverlayActive: Bool = false
    
    /// property to track if any toasts are active in the modifier
    @Published private var hasActiveToasts: Bool = false
    
    /// Task to track loader timeout
    private var loaderTimeoutTask: Task<Void, Never>?
    private let loaderTimeoutDuration: TimeInterval = 600 // 10 minutes
    
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
        !modalViewData.isEmpty
    }
    
    @MainActor func showAlert(_ alert: AlertModel) {
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
        
        self.alertData = wrappedAlert
        self.isOverlayActive = true
    }
    
    @MainActor func dismissAlert() {
        self.alertData = nil
        self.updateOverlayState()
    }
    
    @MainActor func showToast(_ data: ToastModel) {
        var toast = data
        toast.onDismiss = { [weak self] in
            self?.dismissToast()
        }
        /// Set up the active count change handler to update hasActiveToasts
        toast.onActiveCountChanged = { [weak self] count in
            Task { @MainActor in
                self?.hasActiveToasts = count > 0
            }
        }
        
        self.toastData = toast
        self.hasActiveToasts = true
    }
    
    @MainActor func dismissToast() {
        self.toastData = nil
        // Don't immediately set hasActiveToasts to false - let ToastModifier manage this
        self.updateOverlayState()
    }
    
    @MainActor func showLoader(_ loader: LoaderModel) {
        // Cancel any existing timeout task
        loaderTimeoutTask?.cancel()
        self.loaderData = loader
        self.isOverlayActive = true
        
        // Start timeout task
        loaderTimeoutTask = Task { @MainActor in
            do {
                try await Task.sleep(nanoseconds: UInt64(self.loaderTimeoutDuration * 1_000_000_000))
                // Only dismiss if loader is still active and task wasn't cancelled
                if self.loaderData != nil {
                    // Show error after 10 minutes timeout
                    self.showToast(ToastModel(
                        title: ToastStrings.error,
                        message: ToastStrings.somethingWentWrong,
                        duration: 3
                    ))
                    self.dismissLoader()
                }
            } catch {
                // Task was cancelled, ignore
            }
        }
    }
    
    @MainActor func dismissLoader() {
        // Cancel timeout task
        loaderTimeoutTask?.cancel()
        loaderTimeoutTask = nil
        
        self.loaderData = nil
        self.updateOverlayState()
    }
    
    @MainActor func dismissAllNotifications() {
        // Cancel timeout task
        loaderTimeoutTask?.cancel()
        loaderTimeoutTask = nil
        
        self.alertData = nil
        self.toastData = nil
        self.loaderData = nil
        self.modalViewData = []
        self.isOverlayActive = false
    }
    
    @MainActor func showModal(_ modal: ModalData) {
        let wrappedModal = ModalData(
            presentedView: modal.presentedView,
            backdropDismiss: modal.backdropDismiss
        ) {
                modal.onDismiss?()
                self.dismissModal()
            }
        self.modalViewData.append(wrappedModal)
        self.isOverlayActive = true
    }
    
    @MainActor func dismissModal() {
        if !self.modalViewData.isEmpty {
            self.modalViewData.removeLast()
        }
        self.updateOverlayState()
    }
    
    @MainActor func dismissAllModals() {
        self.modalViewData = []
        self.isOverlayActive = false
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
