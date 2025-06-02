import Foundation

enum AccountError: LocalizedError {
    case noActiveAccount
    case accountNotFound(id: String)
    case invalidCredentials
    case networkError(Error)
    case notImplemented
    case unknown(Error)
    case maxAccountsReached
    
    var errorDescription: String? {
        switch self {
        case .noActiveAccount:
            return "No active account found"
        case .accountNotFound(let id):
            return "Account not found with ID: \(id)"
        case .invalidCredentials:
            return "Invalid email or password"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .notImplemented:
            return "This feature is not implemented yet"
        case .unknown(let error):
            return "Unknown error: \(error.localizedDescription)"
        case .maxAccountsReached:
            return "Maximum number of accounts \(AppConstants.Account.maxAccounts) reached. Please remove an account before adding a new one."
        }
    }
}