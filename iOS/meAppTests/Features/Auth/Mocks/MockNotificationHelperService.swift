//
//  MockNotificationHelperService.swift
//  meAppTests
//
//  Subclass of NotificationHelperService that overrides all UI-dispatch methods
//  to set state synchronously, making tests deterministic without DispatchQueue.main.async.
//

import Foundation
@testable import meApp

@MainActor
final class MockNotificationHelperService: NotificationHelperService {

    // MARK: - Call tracking

    var showAlertCallCount = 0
    var lastShownAlert: AlertModel?

    var showToastCallCount = 0
    var lastShownToast: ToastModel?

    var showLoaderCallCount = 0
    var lastShownLoader: LoaderModel?

    var dismissLoaderCallCount = 0
    var dismissAlertCallCount = 0
    var dismissToastCallCount = 0

    var showModalCallCount = 0
    var lastShownModal: ModalData?

    // MARK: - Overrides (synchronous, no DispatchQueue.main.async)

    override func showAlert(_ alert: AlertModel) {
        showAlertCallCount += 1
        lastShownAlert = alert
        alertData = alert
    }

    override func dismissAlert() {
        dismissAlertCallCount += 1
        alertData = nil
    }

    override func showToast(_ data: ToastModel) {
        showToastCallCount += 1
        lastShownToast = data
        toastData = data
    }

    override func dismissToast() {
        dismissToastCallCount += 1
        toastData = nil
    }

    override func showLoader(_ loader: LoaderModel) {
        showLoaderCallCount += 1
        lastShownLoader = loader
        loaderData = loader
    }

    override func dismissLoader() {
        dismissLoaderCallCount += 1
        loaderData = nil
    }

    override func showModal(_ modal: ModalData) {
        showModalCallCount += 1
        lastShownModal = modal
        modalViewData.append(modal)
    }

    // MARK: - Helpers

    func reset() {
        showAlertCallCount = 0
        lastShownAlert = nil
        showToastCallCount = 0
        lastShownToast = nil
        showLoaderCallCount = 0
        lastShownLoader = nil
        dismissLoaderCallCount = 0
        dismissAlertCallCount = 0
        dismissToastCallCount = 0
        showModalCallCount = 0
        lastShownModal = nil
        alertData = nil
        toastData = nil
        loaderData = nil
        modalViewData = []
    }
}
