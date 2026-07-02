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

    /// Suppresses alerts while the launch/loading screen is up, presenting the most
    /// recent queued alert once loading completes (MOB-196). Pass `true` when the
    /// loading screen appears and `false` once the app has finished loading.
    func setAppLoading(_ isLoading: Bool)
}

extension NotificationHelperServiceProtocol {
    /// Default no-op so mocks and non-loading-aware conformers don't need to implement it.
    func setAppLoading(_ isLoading: Bool) {}
}
