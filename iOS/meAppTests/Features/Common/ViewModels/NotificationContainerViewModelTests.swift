import Combine
import Foundation
@testable import meApp
import SwiftUI
import Testing

@Suite(.serialized)
@MainActor
struct NotificationContainerViewModelTests {

    // MARK: - Helpers

    /// Registers a concrete `TestNotificationHelperService` (subclass of the production
    /// `NotificationHelperService`) so the view model — which injects the concrete type —
    /// resolves it and re-publishes its state.
    private func makeSUT() -> (sut: NotificationContainerViewModel, service: TestNotificationHelperService) {
        TestDependencyContainer.reset()
        let service = TestNotificationHelperService()
        DependencyContainer.shared.register(service as NotificationHelperService)
        let sut = NotificationContainerViewModel()
        return (sut, service)
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }

    // MARK: - Initial State

    @Test("initialises with no active notification data")
    func initialStateIsEmpty() {
        let (sut, _) = makeSUT()

        #expect(sut.alertData == nil)
        #expect(sut.toastData == nil)
        #expect(sut.loaderData == nil)
        #expect(sut.modalViewData.isEmpty)
    }

    // MARK: - Alert binding

    @Test("alertData mirrors the service when an alert is shown")
    func alertDataMirrorsService() async {
        let (sut, service) = makeSUT()
        let alert = AlertModel(title: "Title", message: "Message", buttons: [])

        service.showAlert(alert)

        let updated = await waitUntil { sut.alertData?.title == "Title" }
        #expect(updated == true)
        #expect(sut.alertData?.message == "Message")
    }

    // MARK: - Toast binding

    @Test("toastData mirrors the service when a toast is shown")
    func toastDataMirrorsService() async {
        let (sut, service) = makeSUT()

        service.showToast(ToastModel(message: "Hello"))

        let updated = await waitUntil { sut.toastData?.message == "Hello" }
        #expect(updated == true)
    }

    // MARK: - Loader binding

    @Test("loaderData mirrors the service when a loader is shown and cleared")
    func loaderDataMirrorsService() async {
        let (sut, service) = makeSUT()

        service.showLoader(LoaderModel(text: "Loading"))
        let shown = await waitUntil { sut.loaderData != nil }
        #expect(shown == true)

        service.dismissLoader()
        let cleared = await waitUntil { sut.loaderData == nil }
        #expect(cleared == true)
    }

    // MARK: - Modal binding

    @Test("modalViewData mirrors the service modal stack")
    func modalDataMirrorsService() async {
        let (sut, service) = makeSUT()

        service.showModal(ModalData(presentedView: AnyView(EmptyView())))
        let pushed = await waitUntil { sut.modalViewData.count == 1 }
        #expect(pushed == true)

        service.dismissModal()
        let popped = await waitUntil { sut.modalViewData.isEmpty }
        #expect(popped == true)
    }

    // MARK: - dismissToastSignal

    @Test("dismissToastSignal forwards the service dismiss-toast events")
    func dismissToastSignalForwardsEvents() async {
        let (sut, service) = makeSUT()
        var received = 0
        var bag = Set<AnyCancellable>()
        sut.dismissToastSignal
            .sink { received += 1 }
            .store(in: &bag)

        service.dismissToast()

        let fired = await waitUntil { received == 1 }
        #expect(fired == true)
    }
}
