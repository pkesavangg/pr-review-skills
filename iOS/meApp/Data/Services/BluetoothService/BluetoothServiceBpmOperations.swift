//
//  BluetoothServiceBpmOperations.swift
//  BPM device operations: scan, connect, and receive blood pressure readings.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage

@MainActor
extension BluetoothService {

    // MARK: - BPM Scanning

    /// Starts a scan targeting BPM (Blood Pressure Monitor) devices.
    func scanForBpm() {
        logger.log(level: .info, tag: tag, message: "Starting BPM device scan")
        discoveryManager.scanForPairing(using: ggBleSDK)
    }

    // MARK: - BPM Connection

    /// Connects to a BPM device by its broadcast ID and selected user number.
    /// Returns the SDK's user-creation response so the caller can detect user mismatch.
    func connectBpm(broadcastId: String, userNumber: Int) async -> Result<UserCreationResponse, BluetoothServiceError> {
        guard !broadcastId.isEmpty else {
            return .failure(.invalidBroadcastId)
        }

        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            ggDevice.userNumber = userNumber
            let sdkResult = try await withTimeout(seconds: 10) {
                try await self.ggBleSDK.confirmPair(ggDevice)
            }
            logger.log(level: .info, tag: tag, message: "BPM device connected: \(broadcastId), result: \(sdkResult)")
            return .success(UserCreationResponse(sdkType: sdkResult))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            logger.log(level: .error, tag: tag, message: "BPM connect failed: \(error.localizedDescription)")
            return .failure(.pairFailed(error))
        }
    }

    // MARK: - BPM Reading

    /// Requests the latest BPM reading from the connected device.
    /// The reading is delivered via `newBpmReadingReceivedPublisher`.
    func receiveBpmReading(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        guard !broadcastId.isEmpty else {
            return .failure(.invalidBroadcastId)
        }
        logger.log(level: .info, tag: tag, message: "Awaiting BPM reading from device: \(broadcastId)")
        return .success(())
    }

    // MARK: - BPM Scan Response Handling

    /// Handles a blood pressure measurement received from the SDK scan pipeline.
    func handleBpmMeasurement(_ bpmEntry: GGBPMEntry) {
        let systolic = bpmEntry.systolic ?? 0
        let diastolic = bpmEntry.diastolic ?? 0
        let pulse = bpmEntry.pulse ?? 0
        let timestamp: Date = bpmEntry.date.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) } ?? Date()

        let measurement = BpmMeasurement(
            systolic: systolic,
            diastolic: diastolic,
            pulse: pulse,
            timestamp: timestamp,
            broadcastId: bpmEntry.broadcastId
        )

        logger.log(
            level: .info,
            tag: tag,
            message: "BPM reading received: \(systolic)/\(diastolic) pulse=\(pulse)"
        )
        newBpmReadingReceivedSubject.send(measurement)
    }

    /// Converts a BpmMeasurement into an Entry and saves it.
    func saveBpmEntry(_ measurement: BpmMeasurement) async {
        guard let activeAccount = activeAccount else {
            logger.log(level: .error, tag: tag, message: BluetoothServiceError.noActiveAccount.localizedDescription)
            return
        }

        let timestamp = ISO8601DateFormatter().string(from: measurement.timestamp)
        let entry = Entry(
            entryTimestamp: timestamp,
            accountId: activeAccount.accountId,
            operationType: OperationType.create.rawValue,
            deviceType: DeviceType.bpm.rawValue,
            isSynced: false
        )
        entry.scaleEntry = BathScaleEntry(
            source: ScaleSourceType.bluetooth.rawValue,
            systolic: measurement.systolic,
            diastolic: measurement.diastolic,
            meanArterial: measurement.meanArterial
        )
        entry.scaleEntryMetric = BathScaleMetric(
            pulse: measurement.pulse
        )
        entry.bpmEntry = BPMEntry(
            systolic: measurement.systolic,
            diastolic: measurement.diastolic,
            meanArterial: measurement.meanArterial ?? "",
            pulse: measurement.pulse,
            note: ""
        )

        do {
            try await entryService.saveNewEntry(entry)
            let notification = EntryNotification(from: entry)
            newEntryReceivedSubject.send(notification)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save BPM entry: \(error.localizedDescription)")
        }
    }
}
