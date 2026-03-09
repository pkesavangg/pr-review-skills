import Foundation
@testable import meApp

@MainActor
final class MockAppReviewHandler: AppReviewHandlerProtocol {
    private(set) var triggerAppReviewCalls = 0
    private(set) var lastIsFromDebug: Bool?

    func triggerAppReview(isFromDebug: Bool) async {
        triggerAppReviewCalls += 1
        lastIsFromDebug = isFromDebug
    }
}
