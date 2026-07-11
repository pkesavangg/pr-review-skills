import Foundation
@testable import meApp
import Testing
import UIKit

@Suite(.serialized)
@MainActor
struct CopyMacAddressViewModelTests {

    private func makeSUT() -> (sut: CopyMacAddressViewModel, notification: MockNotificationHelperService) {
        TestDependencyContainer.reset()
        let notification = MockNotificationHelperService()
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
        return (CopyMacAddressViewModel(), notification)
    }

    @Test("copyMacAddress copies the value to the system pasteboard")
    func copyMacAddressWritesToPasteboard() {
        let (sut, _) = makeSUT()
        let mac = "AA:BB:CC:DD:EE:FF"

        sut.copyMacAddress(macAddress: mac)

        #expect(UIPasteboard.general.string == mac)
    }

    @Test("copyMacAddress shows the copied-to-clipboard toast")
    func copyMacAddressShowsToast() {
        let (sut, notification) = makeSUT()

        sut.copyMacAddress(macAddress: "11:22:33:44:55:66")

        #expect(notification.showToastCalls == 1)
        #expect(notification.toastData?.message == ToastStrings.copiedToClipboard)
    }

    @Test("copyMacAddress copies an empty string when given an empty address")
    func copyMacAddressEmptyValue() {
        let (sut, notification) = makeSUT()

        sut.copyMacAddress(macAddress: "")

        #expect(UIPasteboard.general.string?.isEmpty == true)
        #expect(notification.showToastCalls == 1)
    }
}
