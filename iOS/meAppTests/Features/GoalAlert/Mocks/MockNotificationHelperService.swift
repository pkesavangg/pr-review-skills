import Foundation
@testable import meApp

@MainActor
final class MockNotificationHelperService: NotificationHelperServiceProtocol {
    var isOverlayActive: Bool = false

    private(set) var alertData: AlertModel?
    private(set) var toastData: ToastModel?
    private(set) var loaderData: LoaderModel?
    private(set) var modalViewData: [ModalData] = []

    private(set) var showAlertCalls = 0
    private(set) var dismissAlertCalls = 0
    private(set) var showToastCalls = 0
    private(set) var dismissToastCalls = 0
    private(set) var showLoaderCalls = 0
    private(set) var dismissLoaderCalls = 0
    private(set) var dismissAllNotificationsCalls = 0
    private(set) var showModalCalls = 0
    private(set) var dismissModalCalls = 0
    private(set) var dismissAllModalsCalls = 0

    var isAlertVisible: Bool { alertData != nil }
    var isToastVisible: Bool { toastData != nil }
    var isLoaderVisible: Bool { loaderData != nil }
    var isModalVisible: Bool { !modalViewData.isEmpty }

    func showAlert(_ alert: AlertModel) {
        showAlertCalls += 1
        alertData = alert
        isOverlayActive = true
    }

    func dismissAlert() {
        dismissAlertCalls += 1
        alertData = nil
        updateOverlayState()
    }

    func showToast(_ data: ToastModel) {
        showToastCalls += 1
        toastData = data
    }

    func dismissToast() {
        dismissToastCalls += 1
        toastData = nil
        updateOverlayState()
    }

    func showLoader(_ loader: LoaderModel) {
        showLoaderCalls += 1
        loaderData = loader
        isOverlayActive = true
    }

    func dismissLoader() {
        dismissLoaderCalls += 1
        loaderData = nil
        updateOverlayState()
    }

    func dismissAllNotifications() {
        dismissAllNotificationsCalls += 1
        alertData = nil
        toastData = nil
        loaderData = nil
        modalViewData = []
        isOverlayActive = false
    }

    func showModal(_ modal: ModalData) {
        showModalCalls += 1
        modalViewData.append(modal)
        isOverlayActive = true
    }

    func dismissModal() {
        dismissModalCalls += 1
        if !modalViewData.isEmpty {
            modalViewData.removeLast()
        }
        updateOverlayState()
    }

    func dismissAllModals() {
        dismissAllModalsCalls += 1
        modalViewData = []
        updateOverlayState()
    }

    private func updateOverlayState() {
        isOverlayActive = isAlertVisible || isLoaderVisible || isModalVisible
    }
}
