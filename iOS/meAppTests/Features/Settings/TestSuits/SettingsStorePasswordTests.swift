import Foundation
@testable import meApp
import Testing

extension SettingsStoreTests {
    @Suite("Password")
    @MainActor
    struct Password {
        @Test("touchAndValidate marks password field touched and validates")
        func touchAndValidateMarksPasswordFieldTouched() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            store.changePasswordForm.currentPassword.value = ""

            store.touchAndValidate(field: .currentPassword)

            #expect(store.changePasswordForm.currentPassword.isTouched == true)
            #expect(store.changePasswordForm.currentPassword.isInvalid == true)
        }

        @Test("touchAndValidate handles new and confirm password")
        func touchAndValidateHandlesNewAndConfirmPassword() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            store.touchAndValidate(field: .newPassword)
            store.touchAndValidate(field: .confirmNewPassword)

            #expect(store.changePasswordForm.newPassword.isTouched == true)
            #expect(store.changePasswordForm.confirmNewPassword.isTouched == true)
        }

        @Test("handleEditingChanged ignores blur when field already touched")
        func handleEditingChangedIgnoresAlreadyTouchedField() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            store.changePasswordForm.currentPassword.markAsTouched()

            store.handleEditingChanged(false, field: .currentPassword)

            #expect(store.changePasswordForm.currentPassword.isTouched == true)
        }

        @Test("handleEditingChanged touches field on blur when untouched")
        func handleEditingChangedTouchesFieldWhenUntouched() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            store.handleEditingChanged(false, field: .newPassword)

            #expect(store.changePasswordForm.newPassword.isTouched == true)
        }

        @Test("handleEditingChanged ignores editing start")
        func handleEditingChangedIgnoresEditingStart() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            store.handleEditingChanged(true, field: .confirmNewPassword)

            #expect(store.changePasswordForm.confirmNewPassword.isTouched == false)
        }

        @Test("confirmDiscardPasswordChanges returns false when user cancels")
        func confirmDiscardPasswordChangesReturnsFalseOnCancel() async {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            store.changePasswordForm.currentPassword.value = "old"
            store.changePasswordForm.currentPassword.markAsDirty()

            let task = Task { await store.confirmDiscardPasswordChanges() }
            await SettingsStoreTestFixtures.waitUntil { notification.alertData != nil }
            notification.alertData?.buttons.last?.action(nil)
            let shouldLeave = await task.value

            #expect(notification.showAlertCalls == 1)
            #expect(shouldLeave == false)
        }

        @Test("confirmDiscardPasswordChanges returns true when user exits")
        func confirmDiscardPasswordChangesReturnsTrueOnExit() async {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            store.changePasswordForm.currentPassword.value = "old"
            store.changePasswordForm.currentPassword.markAsDirty()

            let task = Task { await store.confirmDiscardPasswordChanges() }
            await SettingsStoreTestFixtures.waitUntil { notification.alertData != nil }
            notification.alertData?.buttons.first?.action(nil)
            let shouldLeave = await task.value

            #expect(notification.showAlertCalls == 1)
            #expect(shouldLeave == true)
        }

        @Test("handleChangePasswordExit pristine navigates back immediately")
        func handleChangePasswordExitPristineNavigatesBack() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            let router = Router<SettingsRoute>()
            router.navigate(to: .changePassword)

            store.handleChangePasswordExit(router: router)

            #expect(router.stack.isEmpty)
        }

        @Test("savePassword invalid form does nothing")
        func savePasswordInvalidFormDoesNothing() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.changePasswordForm.currentPassword.value = ""
            store.changePasswordForm.newPassword.value = "123"
            store.changePasswordForm.confirmNewPassword.value = "xyz"
            store.changePasswordForm.validate()

            store.savePassword(router: router)
            await Task.yield()

            #expect(accountService.updatePasswordCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("savePassword success updates password and navigates back")
        func savePasswordSuccessNavigatesBack() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            accountService.updatePasswordResult = .success(())
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            router.navigate(to: .changePassword)
            store.changePasswordForm.currentPassword.value = "oldpass"
            store.changePasswordForm.newPassword.value = "newpassword"
            store.changePasswordForm.confirmNewPassword.value = "newpassword"
            store.changePasswordForm.validate()

            store.savePassword(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updatePasswordCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(accountService.lastUpdatedOldPassword == "oldpass")
            #expect(accountService.lastUpdatedNewPassword == "newpassword")
            #expect(router.stack.isEmpty)
            #expect(notification.showToastCalls == 1)
        }

        @Test("savePassword unauthorized shows restart toast")
        func savePasswordUnauthorizedShowsRestartToast() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            accountService.updatePasswordResult = .failure(HTTPError.unauthorized)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.changePasswordForm.currentPassword.value = "oldpass"
            store.changePasswordForm.newPassword.value = "newpassword"
            store.changePasswordForm.confirmNewPassword.value = "newpassword"
            store.changePasswordForm.validate()

            store.savePassword(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updatePasswordCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.restartAndTryAgain)
        }

        @Test("savePassword bad request shows restart toast")
        func savePasswordBadRequestShowsRestartToast() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            accountService.updatePasswordResult = .failure(HTTPError.badRequest)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.changePasswordForm.currentPassword.value = "oldpass"
            store.changePasswordForm.newPassword.value = "newpassword"
            store.changePasswordForm.confirmNewPassword.value = "newpassword"
            store.changePasswordForm.validate()

            store.savePassword(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updatePasswordCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.restartAndTryAgain)
        }

        @Test("savePassword default error shows generic toast")
        func savePasswordDefaultErrorShowsGenericToast() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            accountService.updatePasswordResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.changePasswordForm.currentPassword.value = "oldpass"
            store.changePasswordForm.newPassword.value = "newpassword"
            store.changePasswordForm.confirmNewPassword.value = "newpassword"
            store.changePasswordForm.validate()

            store.savePassword(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updatePasswordCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.somethingWentWrong)
        }

        @Test("showForgotPasswordAlert presents confirmation alert")
        func showForgotPasswordAlertPresentsConfirmation() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)

            store.showForgotPasswordAlert()

            #expect(notification.showAlertCalls == 1)
            #expect(notification.alertData?.message?.contains("lakshmi@example.com") == true)
        }

        @Test("showForgotPasswordAlert primary action sends reset")
        func showForgotPasswordAlertPrimaryActionSendsReset() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.showForgotPasswordAlert()
            notification.alertData?.buttons.first?.action(nil)
            await SettingsStoreTestFixtures.waitUntil { accountService.requestPasswordResetCalls == 1 }

            #expect(accountService.requestPasswordResetCalls == 1)
        }

        @Test("showForgotPasswordAlert cancel does not send reset")
        func showForgotPasswordAlertCancelDoesNotSendReset() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.showForgotPasswordAlert()
            notification.alertData?.buttons.last?.action(nil)
            await Task.yield()

            #expect(accountService.requestPasswordResetCalls == 0)
        }

        @Test("sendForgotPasswordEmail success shows toast")
        func sendForgotPasswordEmailSuccessShowsToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.sendForgotPasswordEmail()
            await SettingsStoreTestFixtures.waitUntil {
                accountService.requestPasswordResetCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(accountService.lastPasswordResetEmail == "lakshmi@example.com")
            #expect(notification.showLoaderCalls == 1)
            #expect(notification.showToastCalls == 1)
        }

        @Test("sendForgotPasswordEmail with blank email does nothing")
        func sendForgotPasswordEmailBlankEmailDoesNothing() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount(email: "   ")
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.sendForgotPasswordEmail()
            await Task.yield()

            #expect(accountService.requestPasswordResetCalls == 0)
            #expect(notification.showLoaderCalls == 0)
        }

        @Test("confirmDiscardPasswordChanges returns true when pristine")
        func confirmDiscardPasswordChangesReturnsTrueWhenPristine() async {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()

            let shouldLeave = await store.confirmDiscardPasswordChanges()

            #expect(shouldLeave == true)
        }

        @Test("handleChangePasswordExit dirty form confirms and resets on exit")
        func handleChangePasswordExitDirtyFormConfirmsAndResets() {
            let notification = TestNotificationHelperService()
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification)
            let router = Router<SettingsRoute>()
            router.navigate(to: .changePassword)
            store.changePasswordForm.currentPassword.value = "oldpass"
            store.changePasswordForm.currentPassword.markAsDirty()

            store.handleChangePasswordExit(router: router)
            notification.alertData?.buttons.first?.action(nil)

            #expect(notification.showAlertCalls == 1)
            #expect(router.stack.isEmpty)
            #expect(store.changePasswordForm.isDirty == false)
        }

        @Test("savePassword server error shows server toast")
        func savePasswordServerErrorShowsServerToast() async {
            let notification = TestNotificationHelperService()
            let accountService = MockAccountService()
            accountService.updatePasswordResult = .failure(HTTPError.serverError)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)
            let router = Router<SettingsRoute>()
            store.changePasswordForm.currentPassword.value = "oldpass"
            store.changePasswordForm.newPassword.value = "newpassword"
            store.changePasswordForm.confirmNewPassword.value = "newpassword"
            store.changePasswordForm.validate()

            store.savePassword(router: router)
            await SettingsStoreTestFixtures.waitUntil {
                accountService.updatePasswordCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.serverError)
        }

        @Test("resetChangePasswordForm clears entered values")
        func resetChangePasswordFormClearsEnteredValues() {
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT()
            store.changePasswordForm.currentPassword.value = "oldpass"
            store.changePasswordForm.newPassword.value = "newpassword"
            store.changePasswordForm.confirmNewPassword.value = "newpassword"
            store.changePasswordForm.currentPassword.markAsDirty()

            store.resetChangePasswordForm()

            #expect(store.changePasswordForm.currentPassword.value.isEmpty)
            #expect(store.changePasswordForm.newPassword.value.isEmpty)
            #expect(store.changePasswordForm.confirmNewPassword.value.isEmpty)
            #expect(store.changePasswordForm.isDirty == false)
        }

        @Test("sendForgotPasswordEmail failure shows retry toast")
        func sendForgotPasswordEmailFailureShowsRetryToast() async {
            let notification = TestNotificationHelperService()
            let account = SettingsStoreTestFixtures.makeAccount()
            let accountService = MockAccountService()
            accountService.seedAccounts([account], active: account)
            accountService.requestPasswordResetResult = .failure(AccountTestError.apiFailed)
            let (store, _, _, _, _) = SettingsStoreTestFixtures.makeSUT(notification: notification, accountService: accountService)

            store.sendForgotPasswordEmail()
            await SettingsStoreTestFixtures.waitUntil {
                accountService.requestPasswordResetCalls == 1 && notification.dismissLoaderCalls == 1
            }

            #expect(notification.toastData?.message == ToastStrings.pleaseTryAgain)
        }
    }
}
