import Foundation

protocol WifiSetupManaging {
    func isPasswordValid(networkForm: NetworkForm) -> Bool
}

struct WifiSetupManager: WifiSetupManaging {
    func isPasswordValid(networkForm: NetworkForm) -> Bool {
        if networkForm.networkHasNoPassword {
            return networkForm.ssid.isValid
        }
        return networkForm.ssid.isValid && networkForm.password.isValid
    }
}
