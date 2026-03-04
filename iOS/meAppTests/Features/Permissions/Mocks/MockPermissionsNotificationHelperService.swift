import Foundation
@testable import meApp

@MainActor
final class MockPermissionsNotificationHelperService: NotificationHelperServiceProtocol {
    var isOverlayActive: Bool = false
    var isAlertVisible: Bool { currentAlert != nil }
    var isToastVisible: Bool { false }
    var isLoaderVisible: Bool { false }
    var isModalVisible: Bool { false }

    private(set) var shownAlerts: [AlertModel] = []
    private(set) var currentAlert: AlertModel?

    func showAlert(_ alert: AlertModel) {
        shownAlerts.append(alert)
        currentAlert = alert
        isOverlayActive = true
    }

    func dismissAlert() {
        currentAlert = nil
        isOverlayActive = false
    }

    func showToast(_ data: ToastModel) {}
    func dismissToast() {}
    func showLoader(_ loader: LoaderModel) {}
    func dismissLoader() {}
    func dismissAllNotifications() {
        currentAlert = nil
        shownAlerts.removeAll()
        isOverlayActive = false
    }

    func showModal(_ modal: ModalData) {}
    func dismissModal() {}
    func dismissAllModals() {}

    func tapAlertButton(at index: Int, value: String? = nil) {
        guard let alert = currentAlert, alert.buttons.indices.contains(index) else { return }
        let action = alert.buttons[index].action
        currentAlert = nil
        isOverlayActive = false
        action(value)
    }
}

