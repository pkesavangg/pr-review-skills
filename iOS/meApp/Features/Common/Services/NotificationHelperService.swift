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
