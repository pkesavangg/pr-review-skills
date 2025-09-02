//
//  SoftwareUpdateViewModel.swift
//  meApp
//

import Foundation
import SwiftUI

@MainActor
final class SoftwareUpdateViewModel: ObservableObject {
    @Injector var bluetoothService: BluetoothService
    @Injector var notificationService: NotificationHelperService
    @Injector var logger: LoggerService

    @Published var selectedDate: Date = Date()
    @Published var selectedTime: Date = Date()
    @Published var isUpdating: Bool = false
    @Published var updateScheduled: Bool = false

    let scale: Device
    let currentFirmware: String?
    let latestVersion: String?

    private let tag = "SoftwareUpdateViewModel"

    init(scale: Device, currentFirmware: String?, latestVersion: String?) {
        self.scale = scale
        self.currentFirmware = currentFirmware
        self.latestVersion = latestVersion
    }

    var hasUpdate: Bool {
        guard let latest = latestVersion, !latest.isEmpty else { return false }
        guard let current = currentFirmware, !current.isEmpty else { return true }
        return latest != current
    }

    func updateSoftware(isScheduled: Bool) async {
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


