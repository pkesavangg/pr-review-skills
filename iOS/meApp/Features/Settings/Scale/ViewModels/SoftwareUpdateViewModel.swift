//
//  SoftwareUpdateViewModel.swift
//  meApp
//

import Foundation
import SwiftUI

@MainActor
final class SoftwareUpdateViewModel: ObservableObject {
    @Injector var bluetoothService: BluetoothServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var scaleService: ScaleServiceProtocol
    @Injector var logger: LoggerServiceProtocol

    @Published var selectedDate = Date()
    @Published var selectedTime = Date()
    @Published var isUpdating: Bool = false
    @Published var updateScheduled: Bool = false

    private let scaleIdString: String
    let currentFirmware: String?
    let latestVersion: String?

    private var deviceSnapshot: DeviceSnapshot? {
        scaleService.scales.first(where: { $0.id == scaleIdString })
    }

    private let tag = "SoftwareUpdateViewModel"

    init(scale: Device, currentFirmware: String?, latestVersion: String?) {
        self.scaleIdString = scale.id
        self.currentFirmware = currentFirmware
        self.latestVersion = latestVersion
    }

    var hasUpdate: Bool {
        guard let latest = latestVersion, !latest.isEmpty else { return false }
        guard let current = currentFirmware, !current.isEmpty else { return true }
        return latest != current
    }

    func updateSoftware(isScheduled: Bool) async {
        guard let snapshot = deviceSnapshot, snapshot.isConnected else { return }
        let broadcastId = snapshot.broadcastIdString ?? ""
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
        let res = await bluetoothService.updateFirmware(broadcastId: broadcastId, timestamp: ts)
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
