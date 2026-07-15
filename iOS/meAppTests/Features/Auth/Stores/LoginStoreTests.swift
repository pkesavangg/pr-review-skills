import Foundation
@testable import meApp
import SwiftUI
import Testing

@Suite(.serialized)
@MainActor
struct LoginStoreTests {
    @Test("initial state")
    func initialState() {
        let (store, _, _, _) = makeLoginStoreSUT()

        #expect(store.showPassword == false)
        #expect(store.isFormSubmitting == false)
        #expect(store.errorMessage == nil)
        #expect(store.isLoading == false)
        #expect(store.loginForm.email.value.isEmpty)
        #expect(store.loginForm.password.value.isEmpty)
        #expect(store.isFromAccountSwitching == false)
        #expect(store.isFormValid == false)
    }

    @Test("prefill email updates value and keeps control pristine")
    func prefillEmail() {
        let (store, _, _, _) = makeLoginStoreSUT()

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
        let (store, _, _, _) = makeLoginStoreSUT()

        #expect(store.loginForm.email.isTouched == false)
        #expect(store.loginForm.password.isTouched == false)

        store.setEmailTouched()
        store.setPasswordTouched()

        #expect(store.loginForm.email.isTouched == true)
        #expect(store.loginForm.password.isTouched == true)
    }

    @Test("toggle show password")
    func toggleShowPassword() {
        let (store, _, _, _) = makeLoginStoreSUT()

        #expect(store.showPassword == false)
        store.toggleShowPassword()
        #expect(store.showPassword == true)
        store.toggleShowPassword()
        #expect(store.showPassword == false)
    }

    @Test("browser binding clears all browser states")
    func browserBindingClearsState() {
        let (store, _, _, _) = makeLoginStoreSUT()

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
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()

        await store.logIn()

        #expect(accountService.logInCalls == 0)
        #expect(notificationService.isLoaderVisible == false)
        #expect(store.isFormSubmitting == false)
    }

    @Test("logIn success: calls account service and fires success callback")
    func logInSuccess() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .success(())

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

    @Test("logIn trims email before API call")
    func logInTrimsEmailBeforeAPICall() async {
        let (store, accountService, _, _) = makeLoginStoreSUT()
        accountService.logInResult = .success(())

        store.loginForm.email.value = "  user@example.com  "
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(accountService.lastLoginEmail == "user@example.com")
    }

    @Test("logIn account switching: does not fire onLoginSuccess")
    func logInAccountSwitchingSkipsSuccessCallback() async {
        let (store, accountService, _, _) = makeLoginStoreSUT()
        accountService.logInResult = .success(())
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
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .failure(HTTPError.unauthorized)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(notificationService.isToastVisible == true)
        #expect(notificationService.toastData?.title == LoginStoreTestText.loginErrorTitle)
    }

    @Test("logIn network timeout: shows network toast")
    func logInTimeoutError() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .failure(HTTPError.timeout)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(notificationService.isToastVisible == true)
        #expect(notificationService.toastData?.title == LoginStoreTestText.networkErrorTitle)
    }

    @Test("logIn unknown failure: shows generic toast")
    func logInUnknownError() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .failure(AuthStoreTestError.generic)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(notificationService.isToastVisible == true)
        #expect(notificationService.toastData?.title == LoginStoreTestText.loginErrorTitle)
    }

    @Test("logIn max accounts reached: shows max users alert")
    func logInMaxAccountsReached() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .failure(AccountError.maxAccountsReached)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.title == LoginStoreTestText.maxUsersAlertTitle)
        #expect(notificationService.alertData?.message == LoginStoreTestText.maxUsersLoginAndRemoveMessage)
        #expect(notificationService.isLoaderVisible == false)
    }

    @Test("showPasswordResetPrompt sets reset state and shows alert")
    func showPasswordResetPrompt() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()
        store.loginForm.email.value = "  user@example.com  "

        store.showPasswordResetPrompt()

        #expect(store.showResetPrompt == true)
        #expect(store.resetEmail == "user@example.com")
        #expect(store.isPasswordResetAlertVisible == true)
        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.title == LoginStoreTestText.passwordResetAlertTitle)
        #expect(notificationService.alertData?.message == LoginStoreTestText.passwordResetAlertMessage)
        #expect(notificationService.alertData?.inputField?.value == "user@example.com")
    }

    @Test("password reset cancel: hides alert state and triggers dismiss callback")
    func passwordResetCancelAction() {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        store.loginForm.email.value = "user@example.com"

        var dismissCalled = false
        store.onPasswordResetAlertDismissed = { dismissCalled = true }
        store.showPasswordResetPrompt()

        guard let cancelButton = notificationService.alertData?.buttons.first else {
            Issue.record("Expected reset alert cancel button")
            return
        }

        cancelButton.action(nil)

        #expect(dismissCalled == true)
        #expect(store.isPasswordResetAlertVisible == false)
        #expect(accountService.requestPasswordResetCalls == 0)
    }

    @Test("password reset submit: triggers dismiss callback")
    func passwordResetSubmitDismissCallback() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.requestPasswordResetResult = .success(())
        store.loginForm.email.value = "user@example.com"

        var dismissCalled = false
        store.onPasswordResetAlertDismissed = { dismissCalled = true }
        store.showPasswordResetPrompt()

        guard let submitButton = notificationService.alertData?.buttons.last else {
            Issue.record("Expected reset alert submit button")
            return
        }

        submitButton.action(nil)
        await waitUntil { accountService.requestPasswordResetCalls == 1 }

        #expect(dismissCalled == true)
        #expect(store.isPasswordResetAlertVisible == false)
    }

    @Test("password reset invalid email: does not call API and shows validation error")
    func passwordResetInvalidEmailFromAlertSubmit() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
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
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
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
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
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
        #expect(store.resetError == LoginStoreTestText.passwordResetFailed)
    }

    @Test("openPrivacy sets URL and browser flag")
    func openPrivacy() {
        let (store, _, _, _) = makeLoginStoreSUT()

        store.openPrivacy()

        #expect(store.showPrivacyBrowser == true)
        #expect(store.browserURL != nil)
    }

    @Test("openTerms sets URL and browser flag")
    func openTerms() {
        let (store, _, _, _) = makeLoginStoreSUT()

        store.openTerms()

        #expect(store.showTermsBrowser == true)
        #expect(store.browserURL != nil)
    }

    @Test("openHelp shows modal")
    func openHelp() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()

        store.openHelp()

        #expect(notificationService.isModalVisible == true)
        #expect(notificationService.modalViewData.count == 1)
    }

    @Test("handleExit pristine from account switching: calls exit handler")
    func handleExitPristineAccountSwitching() {
        let (store, _, _, _) = makeLoginStoreSUT()
        store.isFromAccountSwitching = true

        var exitCalled = false
        store.onAccountSwitchingExit = { exitCalled = true }

        store.handleExit()

        #expect(exitCalled == true)
    }

    @Test("handleExit pristine from account switching with no handlers does not show alert")
    func handleExitPristineAccountSwitchingNoHandlers() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()
        store.isFromAccountSwitching = true

        store.handleExit()

        #expect(notificationService.isAlertVisible == false)
    }

    @Test("handleExit pristine non-switching: navigates back")
    func handleExitPristineNavigatesBack() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()
        let router = Router<AuthRoute>()
        router.navigate(to: .login(nil))

        #expect(router.stack.count == 1)

        store.handleExit(router: router)

        #expect(router.stack.isEmpty == true)
        #expect(notificationService.isAlertVisible == false)
    }

    @Test("handleExit dirty form: shows exit confirmation alert")
    func handleExitDirtyShowsAlert() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()
        store.loginForm.email.value = "user@example.com"

        store.handleExit()

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.buttons.count == 2)
    }

    @Test("handleExit dirty primary action navigates back")
    func handleExitDirtyPrimaryAction() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()
        let router = Router<AuthRoute>()
        router.navigate(to: .login(nil))
        store.loginForm.email.value = "user@example.com"

        store.handleExit(router: router)
        notificationService.alertData?.buttons.first?.action(nil)

        #expect(router.stack.isEmpty == true)
    }

    @Test("handleExit dirty secondary action keeps screen state")
    func handleExitDirtySecondaryAction() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()
        let router = Router<AuthRoute>()
        router.navigate(to: .login(nil))
        store.loginForm.email.value = "user@example.com"

        store.handleExit(router: router)
        notificationService.alertData?.buttons.last?.action(nil)

        #expect(router.stack.count == 1)
        #expect(store.loginForm.email.value == "user@example.com")
    }

    @Test("logIn account switching with no callbacks does not trigger login success callback")
    func logInAccountSwitchingNoCallbacks() async {
        let (store, accountService, _, _) = makeLoginStoreSUT()
        accountService.logInResult = .success(())
        store.isFromAccountSwitching = true
        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        var successCalled = false
        store.onLoginSuccess = { successCalled = true }

        await store.logIn()

        #expect(successCalled == false)
    }

    @Test("presentingBrowserURL uses fallback when browserURL is nil")
    func presentingBrowserURLFallback() {
        let (store, _, _, _) = makeLoginStoreSUT()
        store.browserURL = nil

        #expect(store.presentingBrowserURL.absoluteString == LoginStoreTestText.baseURL)
    }

    @Test("callbacks can be assigned and executed")
    func callbacksCanBeAssigned() {
        let (store, _, _, _) = makeLoginStoreSUT()

        var loginSuccessCalled = false
        var navigateBackCalled = false

        store.onLoginSuccess = { loginSuccessCalled = true }
        store.onNavigateBack = { navigateBackCalled = true }

        store.onLoginSuccess?()
        store.onNavigateBack?()

        #expect(loginSuccessCalled == true)
        #expect(navigateBackCalled == true)
    }

    // MARK: - Additional handleLoginError branches

    @Test("logIn no internet: shows network toast")
    func logInNoInternetError() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .failure(HTTPError.noInternet)

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(notificationService.isToastVisible == true)
        #expect(notificationService.toastData?.title == LoginStoreTestText.networkErrorTitle)
    }

    @Test("logIn status code zero: shows network toast")
    func logInStatusCodeZeroError() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .failure(HTTPError.statusCode(0))

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(notificationService.isToastVisible == true)
        #expect(notificationService.toastData?.title == LoginStoreTestText.networkErrorTitle)
    }

    // MARK: - Account switching dismissal path

    @Test("logIn success account switching with nil onLoginSuccess uses dismissal path")
    func logInSuccessAccountSwitchingUsesDismissalPath() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .success(())
        store.isFromAccountSwitching = true
        // onLoginSuccess is nil; dismissAction is also nil in tests (can't construct DismissAction)
        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(accountService.logInCalls == 1)
        #expect(notificationService.isToastVisible == false)
        #expect(notificationService.isAlertVisible == false)
        #expect(store.isFormSubmitting == false)
    }

    // MARK: - handleExit dirty + account switching alert buttons

    @Test("handleExit dirty account switching with exit handler: alert primary calls exit handler")
    func handleExitDirtyAccountSwitchingPrimaryButtonWithExitHandler() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()
        store.isFromAccountSwitching = true
        var exitCalled = false
        store.onAccountSwitchingExit = { exitCalled = true }
        store.loginForm.email.value = "user@example.com"

        store.handleExit()
        notificationService.alertData?.buttons.first?.action(nil)

        #expect(notificationService.isAlertVisible == true)
        #expect(exitCalled == true)
    }

    @Test("handleExit dirty account switching without exit handler: alert primary uses dismissal path")
    func handleExitDirtyAccountSwitchingPrimaryButtonNoExitHandler() {
        let (store, _, _, notificationService) = makeLoginStoreSUT()
        let router = Router<AuthRoute>()
        router.navigate(to: .login(nil))
        store.isFromAccountSwitching = true
        store.loginForm.email.value = "user@example.com"

        store.handleExit(router: router)
        notificationService.alertData?.buttons.first?.action(nil)

        // dismissAction?() is called (nil, no-op); router must not pop
        #expect(router.stack.count == 1)
    }

    // MARK: - Max accounts from account switching

    @Test("logIn max accounts reached from account switching: shows swipe-to-remove message")
    func logInMaxAccountsReachedFromAccountSwitching() async {
        let (store, accountService, _, notificationService) = makeLoginStoreSUT()
        accountService.logInResult = .failure(AccountError.maxAccountsReached)
        store.isFromAccountSwitching = true

        store.loginForm.email.value = "user@example.com"
        store.loginForm.password.value = "secret123"

        await store.logIn()

        #expect(notificationService.isAlertVisible == true)
        #expect(notificationService.alertData?.title == LoginStoreTestText.maxUsersAlertTitle)
        #expect(notificationService.alertData?.message == LoginStoreTestText.maxUsersAccountSwitchingMessage)
        #expect(notificationService.isLoaderVisible == false)
    }

    // MARK: - Computed error properties

}

@MainActor
func makeLoginStoreSUT() -> (LoginStore, MockAccountService, MockLoggerService, MockNotificationHelperService) { // swiftlint:disable:this large_tuple
    TestDependencyContainer.reset()

    let accountService = MockAccountService()
    let logger = MockLoggerService()
    let keychain = MockKeychainService()
    let bluetooth = MockBluetoothService()
    let notificationService = MockNotificationHelperService()

    TestDependencyContainer.registerBase(
        logger: logger,
        keychain: keychain,
        bluetooth: bluetooth
    )
    DependencyContainer.shared.register(accountService as AccountServiceProtocol)
    DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)

    notificationService.dismissAllNotifications()
    let store = LoginStore()
    // Pin dependencies on the store instance so async tasks don't re-resolve from global container.
    store.accountService = accountService
    store.logger = logger
    store.notificationService = notificationService
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

private enum LoginStoreTestText {
    static let loginErrorTitle = "Login Error"
    static let networkErrorTitle = "Network Error"
    static let passwordResetFailed = "failed to send password reset email."
    static let passwordResetAlertTitle = "Password Reset"
    static let passwordResetAlertMessage = "Enter your email"
    static let maxUsersAlertTitle = "Maximum Users Reached"
    static let maxUsersLoginAndRemoveMessage = "Log in to a saved account, then open Settings and tap Switch Accounts to remove users."
    static let maxUsersAccountSwitchingMessage = "Please swipe left to remove any unused accounts before attempting to add a new one."
    static let baseURL = "https://greatergoods.com/"
}
