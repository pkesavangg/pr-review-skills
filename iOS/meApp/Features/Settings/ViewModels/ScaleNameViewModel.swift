//
//  ScaleNameViewModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 14/10/25.
//

import SwiftUI

@MainActor
final class ScaleNameViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleService
    @Injector var logger: LoggerService
    
    private let scale: Device
    private let tag = "ScaleNameViewModel"
    
    init(scale: Device) {
        self.scale = scale
    }
    
    func saveScaleName(_ newName: String, onSuccess: (() -> Void)? = nil) async {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
        do {
            _ = try await scaleService.editDevice(scale.id, properties: ["nickname": newName])
            await scaleService.pushLocalChangesToServer()
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.scaleNameUpdated))
            logger.log(level: .info, tag: tag, message: "Scale name updated successfully", data: ["scaleId": scale.id, "newName": newName])
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
        let original = (scale.nickname ?? scale.deviceName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
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
