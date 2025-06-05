//
//  NotificationHelperService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//
import Foundation

@MainActor
class NotificationHelperService: ObservableObject {

    @Published var alertData: AlertModel? = nil
    @Published var toastData: ToastModel? = nil
    @Published var loaderData: LoaderModel? = nil
    @Published var modalViewData: [ModalData] = []
    @Published var isOverlayActive: Bool = false

    var isAlertVisible: Bool {
        alertData != nil
    }

    var isToastVisible: Bool {
        toastData != nil
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
            self.isOverlayActive = false
        }
    }

    func showToast(_ data: ToastModel) {
        DispatchQueue.main.async {
            self.toastData = data
        }
    }

    func dismissToast() {
        DispatchQueue.main.async {
            self.toastData = nil
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
            self.isOverlayActive = false
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
            self.isOverlayActive = self.modalViewData.count > 0
        }
    }
    
    func dismissAllModals() {
        DispatchQueue.main.async {
            self.modalViewData = []
            self.isOverlayActive = false
        }
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
