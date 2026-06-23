//
//  DeviceNameViewModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 14/10/25.
//

import SwiftUI

@MainActor
final class DeviceNameViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var deviceService: PairedDeviceServiceProtocol
    @Injector var logger: LoggerServiceProtocol

    private let scaleIdString: String
    private let initialNickname: String?
    private let tag = "DeviceNameViewModel"

    private var deviceSnapshot: DeviceSnapshot? {
        deviceService.scales.first(where: { $0.id == scaleIdString })
    }

    private var currentNickname: String {
        deviceSnapshot?.nickname ?? deviceSnapshot?.deviceName ?? initialNickname ?? ""
    }

    init(scale: Device) {
        self.scaleIdString = scale.id
        self.initialNickname = scale.nickname ?? scale.deviceName
    }

    func saveDeviceName(_ newName: String, onSuccess: (() -> Void)? = nil) async {
        let deviceId = scaleIdString
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
        do {
            _ = try await deviceService.editDevice(deviceId, properties: ["nickname": newName])
            await deviceService.pushLocalChangesToServer()
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.scaleNameUpdated))
            logger.log(level: .info, tag: tag, message: "Scale name updated successfully", data: ["scaleId": deviceId, "newName": newName])
            onSuccess?()
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save scale name: \(error.localizedDescription)", data: error)
            notificationService.showToast(ToastModel(title: ToastStrings.errorEditingScale, message: ToastStrings.restartApp))
        }
        notificationService.dismissLoader()
    }

    /// Presents a confirm-discard alert; returns true if user confirms exit.
    func confirmDiscardChanges() async -> Bool {
        let alertLang = AlertStrings.EditProfileExitAlert.self
        return await withCheckedContinuation { continuation in
            let alert = AlertModel(
                title: alertLang.title,
                message: alertLang.message,
                buttons: [
                    AlertButtonModel(title: alertLang.exitButton, type: .primary) { _ in
                        continuation.resume(returning: true)
                    },
                    AlertButtonModel(title: alertLang.returnButton, type: .secondary) { _ in
                        continuation.resume(returning: false)
                    }
                ]
            )
            notificationService.showAlert(alert)
        }
    }

    /// Returns true when editedName differs from the scale's current nickname/deviceName.
    func isDirtyComparedToOriginal(editedName: String) -> Bool {
        let original = currentNickname.trimmingCharacters(in: .whitespacesAndNewlines)
        let current  = editedName.trimmingCharacters(in: .whitespacesAndNewlines)
        return current != original
    }

    /// Allows exit when form is not dirty or value unchanged; otherwise prompts for confirmation.
    func allowExit(isFormDirty: Bool, editedName: String) async -> Bool {
        if !isFormDirty || !isDirtyComparedToOriginal(editedName: editedName) {
            return true
        }
        return await confirmDiscardChanges()
    }
}
