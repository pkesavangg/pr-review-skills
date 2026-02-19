//
//  ScaleNameViewModel.swift
//  meApp
//
//  Created by Lakshmi Priya on 14/10/25.
//

import SwiftUI
import SwiftData

@MainActor
final class ScaleNameViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleService
    @Injector var logger: LoggerService

    // R6: Store PersistentIdentifier instead of @Model directly
    private let scaleId: PersistentIdentifier
    private let scaleIdString: String
    private var cachedScale: Device?
    private let tag = "ScaleNameViewModel"

    var scale: Device {
        cachedScale ?? Device(id: scaleIdString, accountId: "")
    }

    init(scale: Device) {
        self.scaleId = scale.persistentModelID
        self.scaleIdString = scale.id
        self.cachedScale = scale
    }

    func refreshScale() {
        let context = PersistenceController.shared.context
        if let fresh: Device = context.registeredModel(for: scaleId) {
            cachedScale = fresh
            return
        }
        let idString = scaleIdString
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == idString })
        cachedScale = try? context.fetch(descriptor).first
    }
    
    func saveScaleName(_ newName: String, onSuccess: (() -> Void)? = nil) async {
        refreshScale()
        let deviceId = scaleIdString
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
        do {
            _ = try await scaleService.editDevice(deviceId, properties: ["nickname": newName])
            await scaleService.pushLocalChangesToServer()
            refreshScale()
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
