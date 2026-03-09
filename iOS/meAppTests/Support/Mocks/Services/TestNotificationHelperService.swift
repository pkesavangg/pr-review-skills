import Foundation
@testable import meApp

@MainActor
final class TestNotificationHelperService: NotificationHelperService {
    private(set) var showAlertCalls = 0
    private(set) var showToastCalls = 0
    private(set) var showLoaderCalls = 0
    private(set) var dismissLoaderCalls = 0
    private(set) var showModalCalls = 0
    private(set) var dismissModalCalls = 0

    override func showAlert(_ alert: AlertModel) {
        showAlertCalls += 1
        super.showAlert(alert)
    }

    override func showToast(_ data: ToastModel) {
        showToastCalls += 1
        super.showToast(data)
    }

    override func showLoader(_ loader: LoaderModel) {
        showLoaderCalls += 1
        super.showLoader(loader)
    }

    override func dismissLoader() {
        dismissLoaderCalls += 1
        super.dismissLoader()
    }

    override func showModal(_ modal: ModalData) {
        showModalCalls += 1
        super.showModal(modal)
    }

    override func dismissModal() {
        dismissModalCalls += 1
        super.dismissModal()
    }
}
