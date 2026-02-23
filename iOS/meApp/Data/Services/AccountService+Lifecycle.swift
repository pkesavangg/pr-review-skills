import Foundation

@MainActor
extension AccountService {
    /// Deletes all accounts locally, logging out each account first.
    /// Logs out non-active accounts first, then the active account last to prevent
    /// premature navigation to the landing screen.
    /// Uses batch logout to prevent state updates until all accounts are logged out.
    func logOutAllAccounts() async throws {
        do {
            logger.log(level: .info, tag: tag, message: "Logout all accounts requested")
            let allAccounts = try await localRepo.fetchAllAccounts()
            
            let nonActiveAccounts = allAccounts.filter { $0.isActiveAccount != true }
            let activeAccountToLogout = allAccounts.first { $0.isActiveAccount == true }
            
            for account in nonActiveAccounts {
                do {
                    guard let localAccount = try await localRepo.fetchAccount(byId: account.accountId) else {
                        continue
                    }
                    try await executeLogout(on: localAccount, isAutoLogout: false, skipStateUpdate: true)
                } catch {
                    logger.log(
                        level: .error,
                        tag: tag,
                        message: "Failed to logout non-active account \(account.accountId): \(error.localizedDescription)"
                    )
                }
            }
            
            if let activeAccount = activeAccountToLogout {
                do {
                    guard let localAccount = try await localRepo.fetchAccount(byId: activeAccount.accountId) else {
                        try await updatePublishedState()
                        return
                    }
                    try await executeLogout(on: localAccount, isAutoLogout: false, skipStateUpdate: true)
                } catch {
                    logger.log(
                        level: .error,
                        tag: tag,
                        message: "Failed to logout active account \(activeAccount.accountId): \(error.localizedDescription)"
                    )
                }
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Error during logout all accounts: \(error.localizedDescription)")
        }
        
        try await updatePublishedState()
        logger.log(level: .info, tag: tag, message: "Logout all accounts completed")
    }

    /// Performs the actual logout logic: API call (ignored if it fails) + local flag updates + state refresh.
    /// - Parameter skipStateUpdate: If true, skips the state update to allow batch operations. Defaults to false.
     func executeLogout(on localAccount: Account, isAutoLogout: Bool, skipStateUpdate: Bool = false) async throws {
        do {
            logger.log(level: .info, tag: tag, message: "Executing logout (API) for accountId=\(localAccount.accountId)")
            try await apiRepo.logOut(fcmToken: localAccount.fcmToken, accountId: localAccount.accountId)
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Logout API call failed for accountId=\(localAccount.accountId): \(error.localizedDescription)"
            )
        }

        do {
            localAccount.isLoggedIn = (localAccount.isLoggedIn ?? false) ? isAutoLogout : false
            localAccount.isActiveAccount = false
            localAccount.isExpired = isAutoLogout
            try await localRepo.updateAccount(localAccount)
            logger.log(level: .info, tag: tag, message: "Local logout flags updated for accountId=\(localAccount.accountId)")
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Local logout flag update failed for accountId=\(localAccount.accountId): \(error.localizedDescription)"
            )
        }

        if !skipStateUpdate {
            try await updatePublishedState()
        }
    }

    /// Migrates account data from Ionic app if needed.
    func migrateFromIonicAppIfNeeded() async throws {
        guard migrationService.isMigrationNeeded() else {
            LoggerService.shared.log(level: .info, tag: tag, message: "No Ionic app migration needed (already completed or no data found)")
            return
        }

        do {
            LoggerService.shared.log(level: .info, tag: tag, message: "Starting comprehensive Ionic app migration (account + scales)")

            let (migratedAccount, scalesCount) = try await migrationService.migrateAccountAndScaleData()

            if let migratedAccount {
                LoggerService.shared.log(
                    level: .info,
                    tag: tag,
                    message: "Ionic app migration completed for account: \(migratedAccount.email) with \(scalesCount) scales"
                )
                try await updatePublishedState()
                LoggerService.shared.log(level: .info, tag: tag, message: "Ionic app migration process completed!")
            } else {
                LoggerService.shared.log(level: .info, tag: tag, message: "No account data was migrated from Ionic app")
            }
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Ionic app migration failed: \(error.localizedDescription)")
        }
    }
}
