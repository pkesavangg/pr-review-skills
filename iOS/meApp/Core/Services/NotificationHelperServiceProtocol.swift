import Foundation

@MainActor
protocol NotificationHelperServiceProtocol: AnyObject {
    var isAlertVisible: Bool { get }
    var isToastVisible: Bool { get }
    var isLoaderVisible: Bool { get }
    var isModalVisible: Bool { get }
    var isOverlayActive: Bool { get set }

    func showAlert(_ alert: AlertModel)
    func dismissAlert()
    func showToast(_ data: ToastModel)
    func dismissToast()
    func showLoader(_ loader: LoaderModel)
    func dismissLoader()
    func dismissAllNotifications()
    func showModal(_ modal: ModalData)
    func dismissModal()
    func dismissAllModals()
}
