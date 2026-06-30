//
//  NotificationHelperServiceTests.swift
//  meAppTests
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct NotificationHelperServiceTests {

    // MARK: - Alert

    @Test("showAlert sets alertData and activates overlay")
    func showAlertSetsAlertDataAndActivatesOverlay() {
        let sut = makeSUT()

        sut.showAlert(makeAlert(title: "Test Alert"))

        #expect(sut.alertData?.title == "Test Alert")
        #expect(sut.isAlertVisible == true)
        #expect(sut.isOverlayActive == true)
    }

    @Test("dismissAlert clears alertData and deactivates overlay")
    func dismissAlertClearsAlertDataAndDeactivatesOverlay() {
        let sut = makeSUT()
        sut.showAlert(makeAlert(title: "Alert"))

        sut.dismissAlert()

        #expect(sut.alertData == nil)
        #expect(sut.isAlertVisible == false)
        #expect(sut.isOverlayActive == false)
    }

    @Test("alert button tap auto-dismisses alert")
    func alertButtonTapAutoDismissesAlert() {
        let sut = makeSUT()
        var actionCalled = false
        let alert = makeAlert(title: "Confirm") { _ in actionCalled = true }
        sut.showAlert(alert)

        // Tap the first button (which is wrapped to auto-dismiss)
        sut.alertData?.buttons.first?.action(nil)

        #expect(actionCalled == true)
        #expect(sut.alertData == nil)
        #expect(sut.isAlertVisible == false)
    }

    @Test("showAlert twice replaces existing alert")
    func showAlertTwiceReplacesExistingAlert() {
        let sut = makeSUT()
        sut.showAlert(makeAlert(title: "First"))
        sut.showAlert(makeAlert(title: "Second"))

        #expect(sut.alertData?.title == "Second")
        #expect(sut.isAlertVisible == true)
    }

    @Test("isAlertVisible reflects alertData presence")
    func isAlertVisibleReflectsAlertDataPresence() {
        let sut = makeSUT()
        #expect(sut.isAlertVisible == false)

        sut.showAlert(makeAlert(title: "X"))
        #expect(sut.isAlertVisible == true)

        sut.dismissAlert()
        #expect(sut.isAlertVisible == false)
    }

    // MARK: - Toast

    @Test("showToast sets toastData and marks toast visible")
    func showToastSetsToastDataAndMarksVisible() {
        let sut = makeSUT()

        sut.showToast(ToastModel(message: "Hello"))

        #expect(sut.toastData?.message == "Hello")
        #expect(sut.isToastVisible == true)
    }

    @Test("dismissToast clears toastData")
    func dismissToastClearsToastData() {
        let sut = makeSUT()
        sut.showToast(ToastModel(message: "Hello"))

        sut.dismissToast()

        #expect(sut.toastData == nil)
    }

    @Test("toast does not affect isOverlayActive")
    func toastDoesNotAffectIsOverlayActive() {
        let sut = makeSUT()

        sut.showToast(ToastModel(message: "Info"))

        #expect(sut.isOverlayActive == false)
    }

    @Test("toast onDismiss callback triggers dismissToast")
    func toastOnDismissCallbackTriggersDismissToast() {
        let sut = makeSUT()
        sut.showToast(ToastModel(message: "Bye"))

        // Invoke the onDismiss closure injected by showToast
        sut.toastData?.onDismiss?()

        #expect(sut.toastData == nil)
    }

    @Test("toast onActiveCountChanged with 0 clears toast visibility")
    func toastOnActiveCountChangedWithZeroClearsVisibility() async {
        let sut = makeSUT()
        sut.showToast(ToastModel(message: "Count"))

        // Invoke the count-change closure with 0 (simulates all toasts dismissed in modifier)
        sut.toastData?.onActiveCountChanged?(0)

        // Give the Task inside onActiveCountChanged a chance to run
        await Task.yield()
        await Task.yield()

        #expect(sut.isToastVisible == false)
    }

    // MARK: - Loader

    @Test("showLoader sets loaderData and activates overlay")
    func showLoaderSetsLoaderDataAndActivatesOverlay() {
        let sut = makeSUT()

        sut.showLoader(LoaderModel(text: "Loading..."))

        #expect(sut.loaderData?.text == "Loading...")
        #expect(sut.isLoaderVisible == true)
        #expect(sut.isOverlayActive == true)
    }

    @Test("dismissLoader clears loaderData and deactivates overlay")
    func dismissLoaderClearsLoaderDataAndDeactivatesOverlay() {
        let sut = makeSUT()
        sut.showLoader(LoaderModel(text: "Loading..."))

        sut.dismissLoader()

        #expect(sut.loaderData == nil)
        #expect(sut.isLoaderVisible == false)
        #expect(sut.isOverlayActive == false)
    }

    @Test("showLoader twice replaces loader without crash")
    func showLoaderTwiceReplacesLoaderWithoutCrash() {
        let sut = makeSUT()

        sut.showLoader(LoaderModel(text: "First"))
        sut.showLoader(LoaderModel(text: "Second"))

        #expect(sut.loaderData?.text == "Second")
        #expect(sut.isLoaderVisible == true)
    }

    @Test("dismissLoader with no active loader does not crash")
    func dismissLoaderWithNoActiveLoaderDoesNotCrash() {
        let sut = makeSUT()
        #expect(sut.loaderData == nil)

        sut.dismissLoader()

        #expect(sut.loaderData == nil)
        #expect(sut.isOverlayActive == false)
    }

    // MARK: - Modal

    @Test("showModal appends to modalViewData and activates overlay")
    func showModalAppendsToModalViewDataAndActivatesOverlay() {
        let sut = makeSUT()

        sut.showModal(ModalData())

        #expect(sut.modalViewData.count == 1)
        #expect(sut.isModalVisible == true)
        #expect(sut.isOverlayActive == true)
    }

    @Test("dismissModal removes last modal (LIFO)")
    func dismissModalRemovesLastModal() {
        let sut = makeSUT()
        sut.showModal(ModalData())
        sut.showModal(ModalData())

        sut.dismissModal()

        #expect(sut.modalViewData.count == 1)
    }

    @Test("multiple modals stack correctly")
    func multipleModalsStackCorrectly() {
        let sut = makeSUT()

        sut.showModal(ModalData())
        sut.showModal(ModalData())
        sut.showModal(ModalData())

        #expect(sut.modalViewData.count == 3)
        #expect(sut.isModalVisible == true)
    }

    @Test("dismissAllModals clears all modals and deactivates overlay")
    func dismissAllModalsClearsAllModalsAndDeactivatesOverlay() {
        let sut = makeSUT()
        sut.showModal(ModalData())
        sut.showModal(ModalData())

        sut.dismissAllModals()

        #expect(sut.modalViewData.isEmpty == true)
        #expect(sut.isModalVisible == false)
        #expect(sut.isOverlayActive == false)
    }

    @Test("dismissModal on empty stack does not crash")
    func dismissModalOnEmptyStackDoesNotCrash() {
        let sut = makeSUT()
        #expect(sut.modalViewData.isEmpty)

        sut.dismissModal()

        #expect(sut.modalViewData.isEmpty)
        #expect(sut.isOverlayActive == false)
    }

    @Test("isModalVisible reflects modalViewData emptiness")
    func isModalVisibleReflectsModalViewDataEmptiness() {
        let sut = makeSUT()
        #expect(sut.isModalVisible == false)

        sut.showModal(ModalData())
        #expect(sut.isModalVisible == true)

        sut.dismissModal()
        #expect(sut.isModalVisible == false)
    }

    @Test("showModal onDismiss callback fires when wrapped dismiss is called")
    func showModalOnDismissCallbackFiresOnDismiss() {
        let sut = makeSUT()
        var dismissCalled = false
        let modal = ModalData { dismissCalled = true }

        sut.showModal(modal)
        // The wrapped modal's onDismiss triggers the original callback + dismissModal
        sut.modalViewData.last?.onDismiss?()

        #expect(dismissCalled == true)
        #expect(sut.modalViewData.isEmpty)
    }

    // MARK: - Composite / dismissAllNotifications

    @Test("dismissAllNotifications clears all state and deactivates overlay")
    func dismissAllNotificationsClearsAllState() {
        let sut = makeSUT()
        sut.showAlert(makeAlert(title: "A"))
        sut.showToast(ToastModel(message: "T"))
        sut.showLoader(LoaderModel())
        sut.showModal(ModalData())

        sut.dismissAllNotifications()

        #expect(sut.alertData == nil)
        #expect(sut.toastData == nil)
        #expect(sut.loaderData == nil)
        #expect(sut.modalViewData.isEmpty)
        #expect(sut.isOverlayActive == false)
    }

    @Test("overlay stays active while any non-toast notification remains, clears when all dismissed")
    func overlayStaysActiveWhileNonToastRemainsAndClearsWhenAllDismissed() {
        let sut = makeSUT()
        sut.showAlert(makeAlert(title: "A"))
        sut.showLoader(LoaderModel())

        #expect(sut.isOverlayActive == true)

        sut.dismissAlert()
        #expect(sut.isOverlayActive == true) // loader still active

        sut.dismissLoader()
        #expect(sut.isOverlayActive == false) // all cleared
    }

    // MARK: - Helpers

    private func makeSUT() -> NotificationHelperService {
        NotificationHelperService()
    }

    private func makeAlert(title: String, buttonAction: ((String?) -> Void)? = nil) -> AlertModel {
        AlertModel(
            title: title,
            buttons: [
                AlertButtonModel(title: "OK", type: .primary) { value in
                    buttonAction?(value)
                }
            ]
        )
    }
}
