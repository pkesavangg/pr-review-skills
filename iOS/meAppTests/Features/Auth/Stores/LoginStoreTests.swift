import Foundation
import SwiftUI
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct LoginStoreTests {
    @Test("initial state")
    func initialState() {
        let (store, _, _, _) = makeSUT()

        #expect(store.showPassword == false)
        #expect(store.isFormSubmitting == false)
        #expect(store.errorMessage == nil)
        #expect(store.isLoading == false)
        #expect(store.loginForm.email.value == "")
        #expect(store.loginForm.password.value == "")
        #expect(store.isFromAccountSwitching == false)
        #expect(store.isFormValid == false)
    }

    @Test("prefill email updates value and keeps control pristine")
    func prefillEmail() {
        let (store, _, _, _) = makeSUT()

        store.prefillEmailIfNeeded("prefilled@example.com")

        #expect(store.loginForm.email.value == "prefilled@example.com")
        #expect(store.loginForm.email.isDirty == false)

        store.prefillEmailIfNeeded(nil)
        #expect(store.loginForm.email.value == "prefilled@example.com")

        store.prefillEmailIfNeeded("")
        #expect(store.loginForm.email.value == "prefilled@example.com")
    }

    @Test("touch helpers mark controls touched")
    func touchHelpers() {
        let (store, _, _, _) = makeSUT()

        #expect(store.loginForm.email.isTouched == false)
        #expect(store.loginForm.password.isTouched == false)

        store.setEmailTouched()
        store.setPasswordTouched()

        #expect(store.loginForm.email.isTouched == true)
        #expect(store.loginForm.password.isTouched == true)
    }

    @Test("toggle show password")
    func toggleShowPassword() {
        let (store, _, _, _) = makeSUT()

        #expect(store.showPassword == false)
        store.toggleShowPassword()
        #expect(store.showPassword == true)
        store.toggleShowPassword()
        #expect(store.showPassword == false)
    }

    @Test("browser binding clears all browser states")
    func browserBindingClearsState() {
        let (store, _, _, _) = makeSUT()

        store.showPrivacyBrowser = true
        store.showTermsBrowser = true
        store.showHelpBrowser = true
        store.browserURL = URL(string: "https://example.com")

        #expect(store.isBrowserPresented.wrappedValue == true)

        store.isBrowserPresented.wrappedValue = false

        #expect(store.showPrivacyBrowser == false)
        #expect(store.showTermsBrowser == false)
        #expect(store.showHelpBrowser == false)
        #expect(store.browserURL == nil)
        #expect(store.isBrowserPresented.wrappedValue == false)
    }

    @Test("logIn invalid form: exits early")
    func logInInvalidForm() async {
        let (store, accountService, _, notificationService) = makeSUT()

        await store.logIn()

        #expect(accountService.logInCalls == 0)
        #expect(notificationService.isLoaderVisible == false)
        #expect(store.isFormSubmitting == false)
    }

    @Test("logIn success: calls account service and fires success callback")
    func logInSuccess() async {
        let (store, accountService, _, notificationService) = makeSUT()
        accountService.logInResult = .success(AuthTestFixtures.makeAccount(email: "user@example.com"))

        var didSucceed = false
        store.onLoginSuccess = { didSucceed = true }
        store.loginForm.email.value = " user@example.com "
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(accountService.lastLoginEmail == "user@example.com")
        #expect(accountService.lastLoginPassword == "secret123")
        #expect(didSucceed == true)
        #expect(store.isFormSubmitting == false)
        #expect(notificationService.isLoaderVisible == false)
    }

    @Test("logIn account switching: does not fire onLoginSuccess")
    func logInAccountSwitchingSkipsSuccessCallback() async {
        let (store, accountService, _, _) = makeSUT()
        accountService.logInResult = .success(AuthTestFixtures.makeAccount())
        store.isFromAccountSwitching = true

        var didSucceed = false
        store.onLoginSuccess = { didSucceed = true }
        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(didSucceed == false)
    }

    @Test("logIn unauthorized: shows invalid credentials toast")
    func logInUnauthorizedError() async {
        let (store, accountService, _, notificationService) = makeSUT()
        accountService.logInResult = .failure(HTTPError.unauthorized)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(notificationService.isToastVisible == true)
        #expect(notificationService.toastData?.title == ToastStrings.loginError)
    }

    @Test("logIn network timeout: shows network toast")
    func logInTimeoutError() async {
        let (store, accountService, _, notificationService) = makeSUT()
        accountService.logInResult = .failure(HTTPError.timeout)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(notificationService.isToastVisible == true)
        #expect(notificationService.toastData?.title == ToastStrings.networkError)
    }

    @Test("logIn unknown failure: shows generic toast")
    func logInUnknownError() async {
        let (store, accountService, _, notificationService) = makeSUT()
        accountService.logInResult = .failure(AuthStoreTestError.generic)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(notificationService.isToastVisible == true)
        #expect(notificationService.toastData?.title == ToastStrings.loginError)
    }

    @Test("logIn max accounts reached: shows max users alert")
    func logInMaxAccountsReached() async {
        let (store, accountService, _, notificationService) = makeSUT()
        accountService.logInResult = .failure(AccountError.maxAccountsReached)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.title == AlertStrings.MaxUsersAlert.title)
    }

    @Test("showPasswordResetPrompt sets reset state and shows alert")
    func showPasswordResetPrompt() {
        let (store, _, _, notificationService) = makeSUT()
        store.loginForm.email.value = "  user@example.com  "

        store.showPasswordResetPrompt()

        #expect(store.showResetPrompt == true)
        #expect(store.resetEmail == "user@example.com")
        #expect(store.isPasswordResetAlertVisible == true)
        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.inputField?.value == "user@example.com")
    }

    @Test("password reset invalid email: does not call API and shows validation error")
    func passwordResetInvalidEmailFromAlertSubmit() async {
        let (store, accountService, _, notificationService) = makeSUT()
        store.loginForm.email.value = "bad-email"
        store.showPasswordResetPrompt()

        guard let submitButton = notificationService.alertData?.buttons.last else {
            Issue.record("Expected reset alert submit button")
            return
        }

        submitButton.action("bad-email")
        await drainMainActorTasks()

        #expect(accountService.requestPasswordResetCalls == 0)
        #expect(store.resetError != nil)
        #expect(notificationService.isToastVisible == true)
    }

    @Test("password reset success: calls API and clears prompt")
    func passwordResetSuccessFromAlertSubmit() async {
        let (store, accountService, _, notificationService) = makeSUT()
        accountService.requestPasswordResetResult = .success(())
        store.loginForm.email.value = "user@example.com"
        store.showPasswordResetPrompt()

        guard let submitButton = notificationService.alertData?.buttons.last else {
            Issue.record("Expected reset alert submit button")
            return
        }

        submitButton.action(nil)
        await waitUntil {
            accountService.requestPasswordResetCalls == 1
        }

        #expect(accountService.requestPasswordResetCalls == 1)
        #expect(accountService.lastPasswordResetEmail == "user@example.com")
        #expect(store.showResetPrompt == false)
        #expect(store.resetError == nil)
        #expect(notificationService.isLoaderVisible == false)
    }

    @Test("password reset failure: sets resetError")
    func passwordResetFailureFromAlertSubmit() async {
        let (store, accountService, _, notificationService) = makeSUT()
        accountService.requestPasswordResetResult = .failure(AuthStoreTestError.generic)
        store.loginForm.email.value = "user@example.com"
        store.showPasswordResetPrompt()

        guard let submitButton = notificationService.alertData?.buttons.last else {
            Issue.record("Expected reset alert submit button")
            return
        }

        submitButton.action("user@example.com")
        await waitUntil {
            accountService.requestPasswordResetCalls == 1 || store.resetError != nil
        }

        #expect(accountService.requestPasswordResetCalls == 1)
        #expect(store.resetError == FormErrorMessages.passwordResetFailed)
    }

    @Test("openPrivacy sets URL and browser flag")
    func openPrivacy() {
        let (store, _, _, _) = makeSUT()

        store.openPrivacy()

        #expect(store.showPrivacyBrowser == true)
        #expect(store.browserURL != nil)
    }

    @Test("openTerms sets URL and browser flag")
    func openTerms() {
        let (store, _, _, _) = makeSUT()

        store.openTerms()

        #expect(store.showTermsBrowser == true)
        #expect(store.browserURL != nil)
    }

    @Test("openHelp shows modal")
    func openHelp() {
        let (store, _, _, notificationService) = makeSUT()

        store.openHelp()

        #expect(notificationService.isModalVisible == true)
        #expect(notificationService.modalViewData.count == 1)
    }

    @Test("handleExit pristine from account switching: calls exit handler")
    func handleExitPristineAccountSwitching() {
        let (store, _, _, _) = makeSUT()
        store.isFromAccountSwitching = true

        var exitCalled = false
        store.onAccountSwitchingExit = { exitCalled = true }

        store.handleExit()

        #expect(exitCalled == true)
    }

    @Test("handleExit pristine non-switching: navigates back")
    func handleExitPristineNavigatesBack() {
        let (store, _, _, _) = makeSUT()
        let router = Router<AuthRoute>()
        router.navigate(to: .login(nil))

        #expect(router.stack.count == 1)

        store.handleExit(router: router)

        #expect(router.stack.isEmpty == true)
    }

    @Test("handleExit dirty form: shows exit confirmation alert")
    func handleExitDirtyShowsAlert() {
        let (store, _, _, notificationService) = makeSUT()
        store.loginForm.email.value = "user@example.com"

        store.handleExit()

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.buttons.count == 2)
    }

    @Test("callbacks can be assigned and executed")
    func callbacksCanBeAssigned() {
        let (store, _, _, _) = makeSUT()

        var loginSuccessCalled = false
        var navigateBackCalled = false

        store.onLoginSuccess = { loginSuccessCalled = true }
        store.onNavigateBack = { navigateBackCalled = true }

        store.onLoginSuccess?()
        store.onNavigateBack?()

        #expect(loginSuccessCalled == true)
        #expect(navigateBackCalled == true)
    }
}

@MainActor
private func makeSUT() -> (LoginStore, MockAccountService, MockLoggerService, NotificationHelperService) {
    TestDependencyContainer.reset()

    let accountService = MockAccountService()
    let logger = MockLoggerService()
    let keychain = MockKeychainService()
    let bluetooth = MockBluetoothService()
    let notificationService = NotificationHelperService()

    TestDependencyContainer.registerBase(
        logger: logger,
        keychain: keychain,
        bluetooth: bluetooth
    )
    DependencyContainer.shared.register(accountService as AccountServiceProtocol)
    DependencyContainer.shared.register(notificationService)
    DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)

    notificationService.dismissAllNotifications()
    let store = LoginStore()
    return (store, accountService, logger, notificationService)
}

@MainActor
private func drainMainActorTasks(iterations: Int = 5) async {
    for _ in 0..<iterations {
        await Task.yield()
    }
}

@MainActor
private func waitUntil(
    timeoutIterations: Int = 200,
    condition: @MainActor () -> Bool
) async {
    for _ in 0..<timeoutIterations {
        if condition() {
            return
        }
        await Task.yield()
    }
}

private enum AuthStoreTestError: Error {
    case generic
}
