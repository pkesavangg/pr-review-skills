import Foundation
@testable import meApp
import SwiftUI
import Testing

@MainActor
extension LoginStoreTests {
    @Test("emailError is nil when email field is clean")
    func emailErrorNilWhenClean() {
        let (store, _, _, _) = makeLoginStoreSUT()

        #expect(store.emailError == nil)
    }

    @Test("emailError returns error string when field is dirty and invalid")
    func emailErrorSetWhenDirtyAndInvalid() {
        let (store, _, _, _) = makeLoginStoreSUT()

        store.loginForm.email.markAsDirty()
        store.loginForm.email.validate()

        #expect(store.emailError != nil)
    }

    @Test("passwordError returns error string when field is dirty and invalid")
    func passwordErrorSetWhenDirtyAndInvalid() {
        let (store, _, _, _) = makeLoginStoreSUT()

        store.loginForm.password.markAsDirty()
        store.loginForm.password.validate()

        #expect(store.passwordError != nil)
    }
}
