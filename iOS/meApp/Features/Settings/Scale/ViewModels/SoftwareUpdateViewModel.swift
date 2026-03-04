//
//  SoftwareUpdateViewModel.swift
//  meApp
//

import Foundation
import SwiftData
import SwiftUI

@MainActor
final class SoftwareUpdateViewModel: ObservableObject {
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var notificationService: NotificationHelperService
    @Injector var logger: LoggerServiceProtocol

    @Published var selectedDate = Date()
    @Published var selectedTime = Date()
    @Published var isUpdating: Bool = false
    @Published var updateScheduled: Bool = false

    // R6: Store PersistentIdentifier instead of @Model directly
    private let scaleId: PersistentIdentifier
    private let scaleIdString: String
    private var cachedScale: Device?
    let currentFirmware: String?
    let latestVersion: String?

    var scale: Device {
        cachedScale ?? Device(id: scaleIdString, accountId: "")
    }

    private let tag = "SoftwareUpdateViewModel"

    init(scale: Device, currentFirmware: String?, latestVersion: String?) {
        self.scaleId = scale.persistentModelID
        self.scaleIdString = scale.id
        self.cachedScale = scale
        self.currentFirmware = currentFirmware
        self.latestVersion = latestVersion
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

    var hasUpdate: Bool {
        guard let latest = latestVersion, !latest.isEmpty else { return false }
        guard let current = currentFirmware, !current.isEmpty else { return true }
        return latest != current
    }

    func updateSoftware(isScheduled: Bool) async {
        refreshScale()
        guard scale.isConnected == true else { return }
        isUpdating = true
        let ts: UInt32 = {
            if isScheduled {
                // Combine the selected date and time into a single timestamp (seconds)
                var calendar = Calendar.current
                calendar.timeZone = .current

                let dateComponents = calendar.dateComponents([.year, .month, .day], from: selectedDate)
                let timeComponents = calendar.dateComponents([.hour, .minute, .second], from: selectedTime)

                var merged = DateComponents()
                merged.year = dateComponents.year
                merged.month = dateComponents.month
                merged.day = dateComponents.day
                merged.hour = timeComponents.hour
                merged.minute = timeComponents.minute
                merged.second = timeComponents.second

                let combinedDate = calendar.date(from: merged) ?? selectedDate
                let base = UInt32(combinedDate.timeIntervalSince1970.rounded())
                return base + 20
            } else {
                return 0
            }
        }()
        notificationService.showToast(ToastModel(title: "Updating", message: FirmwareUpdateStrings.updatingFirmware))
        refreshScale() // R11: refresh before passing @Model to async bluetooth call
        let res = await bluetoothService.updateFirmware(on: scale, timestamp: ts)
        switch res {
        case .success:
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: FirmwareUpdateStrings.updateTriggered))
        case .failure(let err):
            logger.log(level: .error, tag: tag, message: "Firmware update failed: \(err.localizedDescription)")
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.pleaseTryAgain))
        }
        isUpdating = false
    }
}
