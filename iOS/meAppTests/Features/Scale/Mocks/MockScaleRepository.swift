import Foundation
import SwiftData
@testable import meApp

@MainActor
final class MockScaleRepository: DeviceRepositoryProtocol {
    let context: ModelContext
    var devices: [Device] = [] {
        didSet {
            guard !isSyncingSnapshot else { return }
            replaceContextSnapshot(with: devices)
        }
    }

    var createScaleError: Error?
    var patchScalePreferenceError: Error?
    var editScaleError: Error?
    var patchScaleMetaError: Error?
    var deleteScaleError: Error?
    var updateDeviceError: Error?
    var listScalesError: Error?
    var getDeviceError: Error?
    var getUnsyncedDevicesError: Error?
    var getDevicesMarkedForDeletionError: Error?
    var markDeviceAsDeletedError: Error?
    var permanentlyRemoveDeviceError: Error?
    var clearAllDataError: Error?

    private(set) var clearAllDataCalls = 0
    private(set) var createScaleCalls = 0
    private(set) var editScaleCalls = 0
    private(set) var deleteScaleCalls = 0
    private(set) var updateDeviceCalls = 0
    private(set) var patchScaleMetaCalls = 0
    private(set) var patchScalePreferenceCalls = 0
    private(set) var markDeviceAsDeletedCalls = 0
    private(set) var permanentlyRemoveDeviceCalls = 0
    private(set) var replaceAllDevicesForAccountCalls = 0
    private(set) var lastEditedScaleId: String?
    private(set) var lastEditProperties: [String: Any]?
    private(set) var lastPatchedMetaScaleId: String?
    private(set) var lastPatchedMetaData: DeviceMetaData?
    private(set) var lastPatchedPreferenceScaleId: String?
    private(set) var lastPatchedPreferenceDTO: R4ScalePreferenceDTO?
    private(set) var lastReplacedAccountId: String?
    private(set) var lastServerDevices: [DeviceDTO] = []
    private(set) var lastPreservedUnsyncedDevices: [Device] = []
    private var isSyncingSnapshot = false

    init() {
        do {
            let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
            let container = try ModelContainer(
                for: Device.self,
                BathScale.self,
                R4ScalePreference.self,
                DeviceMetaData.self,
                configurations: configuration
            )
            self.context = ModelContext(container)
        } catch {
            fatalError("Failed to create in-memory ModelContainer: \(error)")
        }
    }

    func clearAllData() async throws {
        clearAllDataCalls += 1
        if let clearAllDataError { throw clearAllDataError }
        for device in fetchAllDevices() {
            context.delete(device)
        }
        try context.save()
        syncSnapshotFromContext()
    }

    func listScales(forAccountId accountId: String) async throws -> [Device] {
        if let listScalesError { throw listScalesError }
        syncSnapshotFromContext()
        return devices.filter { $0.accountId == accountId }
    }

    func listScales() async throws -> [Device] {
        if let listScalesError { throw listScalesError }
        syncSnapshotFromContext()
        return devices
    }

    func getDevice(_ deviceId: String) async throws -> Device? {
        if let getDeviceError { throw getDeviceError }
        syncSnapshotFromContext()
        return devices.first { $0.id == deviceId }
    }

    func updateDevice(_ device: Device) async throws {
        updateDeviceCalls += 1
        if let updateDeviceError { throw updateDeviceError }
        deleteFromContext(id: device.id)
        context.insert(clone(device))
        try context.save()
        syncSnapshotFromContext()
    }

    func updateDeviceWithNewId(oldId: String, updatedDevice: Device) async throws {
        deleteFromContext(id: oldId)
        deleteFromContext(id: updatedDevice.id)
        context.insert(clone(updatedDevice))
        try context.save()
        syncSnapshotFromContext()
    }

    func getUnsyncedDevices() async throws -> [Device] {
        if let getUnsyncedDevicesError { throw getUnsyncedDevicesError }
        syncSnapshotFromContext()
        return devices.filter { ($0.isSynced ?? false) == false }
    }

    func createScale(_ scale: Device) async throws -> Device {
        createScaleCalls += 1
        if let createScaleError { throw createScaleError }
        context.insert(clone(scale))
        try context.save()
        syncSnapshotFromContext()
        return scale
    }

    func editScale(_ scaleId: String, properties: [String: Any]) async throws -> Device {
        editScaleCalls += 1
        lastEditedScaleId = scaleId
        lastEditProperties = properties
        if let editScaleError { throw editScaleError }
        guard let device = try await getDevice(scaleId) else {
            throw DeviceError.deviceNotFound(id: scaleId)
        }
        if let nickname = properties["nickname"] as? String {
            device.nickname = nickname
        }
        try context.save()
        syncSnapshotFromContext()
        return device
    }

    func deleteScale(_ scaleId: String) async throws {
        deleteScaleCalls += 1
        if let deleteScaleError { throw deleteScaleError }
        deleteFromContext(id: scaleId)
        try context.save()
        syncSnapshotFromContext()
    }

    func patchScaleMeta(_ scaleId: String, metaData: DeviceMetaData) async throws {
        patchScaleMetaCalls += 1
        lastPatchedMetaScaleId = scaleId
        lastPatchedMetaData = metaData
        if let patchScaleMetaError { throw patchScaleMetaError }
        guard let device = try await getDevice(scaleId) else {
            throw DeviceError.deviceNotFound(id: scaleId)
        }
        device.metaData = cloneMetaData(metaData)
        try context.save()
        syncSnapshotFromContext()
    }

    func patchScalePreference(_ scaleId: String, _ preference: R4ScalePreference) async throws {
        try await patchScalePreference(scaleId, fromDTO: preference.toDTO())
    }

    func patchScalePreference(_ scaleId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {
        patchScalePreferenceCalls += 1
        lastPatchedPreferenceScaleId = scaleId
        lastPatchedPreferenceDTO = dto
        if let patchScalePreferenceError { throw patchScalePreferenceError }
        guard let device = try await getDevice(scaleId) else {
            throw DeviceError.deviceNotFound(id: scaleId)
        }
        if let existing = device.r4ScalePreference {
            existing.displayName = dto.displayName
            existing.displayMetrics = dto.displayMetrics
            existing.shouldFactoryReset = dto.shouldFactoryReset
            existing.shouldMeasureImpedance = dto.shouldMeasureImpedance
            existing.shouldMeasurePulse = dto.shouldMeasurePulse
            existing.timeFormat = dto.timeFormat
            existing.tzOffset = dto.tzOffset
            existing.wifiFotaScheduleTime = dto.wifiFotaScheduleTime
            existing.updatedAt = dto.updatedAt
            existing.isSynced = dto.isSynced ?? false
        } else {
            let preference = R4ScalePreference(from: dto, scaleId: scaleId)
            preference.isSynced = dto.isSynced ?? false
            device.r4ScalePreference = preference
        }
        device.isSynced = false
        try context.save()
        syncSnapshotFromContext()
    }

    func replaceAllDevicesForAccount(_ accountId: String, with serverDevices: [DeviceDTO], preserveUnsynced unsyncedDevices: [Device]) async throws {
        replaceAllDevicesForAccountCalls += 1
        lastReplacedAccountId = accountId
        lastServerDevices = serverDevices
        lastPreservedUnsyncedDevices = unsyncedDevices
        for device in fetchAllDevices().filter({ $0.accountId == accountId }) {
            context.delete(device)
        }
        for device in unsyncedDevices {
            context.insert(clone(device))
        }
        for dto in serverDevices {
            context.insert(Device(from: dto, accountId: accountId))
        }
        try context.save()
        syncSnapshotFromContext()
    }

    func markDeviceAsDeleted(_ deviceId: String) async throws {
        markDeviceAsDeletedCalls += 1
        if let markDeviceAsDeletedError { throw markDeviceAsDeletedError }
        if let device = try await getDevice(deviceId) {
            device.isSoftDeleted = true
        }
        try context.save()
        syncSnapshotFromContext()
    }

    func getDevicesMarkedForDeletion() async throws -> [Device] {
        if let getDevicesMarkedForDeletionError { throw getDevicesMarkedForDeletionError }
        syncSnapshotFromContext()
        return devices.filter { $0.isSoftDeleted == true }
    }

    func permanentlyRemoveDevice(_ deviceId: String) async throws {
        permanentlyRemoveDeviceCalls += 1
        if let permanentlyRemoveDeviceError { throw permanentlyRemoveDeviceError }
        deleteFromContext(id: deviceId)
        try context.save()
        syncSnapshotFromContext()
    }

    func isDevicePurelyLocal(_ deviceId: String) async throws -> Bool {
        try await getDevice(deviceId)?.hasServerID == false
    }

    func fetchAttachedPreference(by id: String) -> R4ScalePreference? {
        syncSnapshotFromContext()
        return devices.first(where: { $0.id == id })?.r4ScalePreference
    }

    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? {
        fetchAttachedPreference(by: id)
    }

    private func replaceContextSnapshot(with devices: [Device]) {
        for device in fetchAllDevices() {
            context.delete(device)
        }
        for device in devices {
            context.insert(clone(device))
        }
        try? context.save()
        syncSnapshotFromContext()
    }

    private func syncSnapshotFromContext() {
        isSyncingSnapshot = true
        devices = fetchAllDevices()
        isSyncingSnapshot = false
    }

    private func fetchAllDevices() -> [Device] {
        (try? context.fetch(FetchDescriptor<Device>())) ?? []
    }

    private func deleteFromContext(id: String) {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == id })
        if let existing = try? context.fetch(descriptor) {
            for device in existing {
                context.delete(device)
            }
        }
    }

    private func clone(_ device: Device) -> Device {
        let copy = Device(
            id: device.id,
            accountId: device.accountId,
            peripheralIdentifier: device.peripheralIdentifier,
            nickname: device.nickname,
            sku: device.sku,
            mac: device.mac,
            password: device.password,
            isSoftDeleted: device.isSoftDeleted,
            deviceName: device.deviceName,
            deviceType: device.deviceType,
            broadcastId: device.broadcastId,
            broadcastIdString: device.broadcastIdString,
            userNumber: device.userNumber,
            protocolType: device.protocolType,
            createdAt: device.createdAt,
            lastModified: device.lastModified,
            isSynced: device.isSynced,
            hasServerID: device.hasServerID,
            isConnected: device.isConnected,
            wifiMac: device.wifiMac,
            isWifiConfigured: device.isWifiConfigured,
            token: device.token,
            isWeighOnlyModeEnabledByOthers: device.isWeighOnlyModeEnabledByOthers
        )
        if let bathScale = device.bathScale {
            copy.bathScale = BathScale(scaleType: bathScale.scaleType, bodyComp: bathScale.bodyComp)
        }
        if let preference = device.r4ScalePreference {
            let dto = preference.toDTO()
            let clonedPreference = R4ScalePreference(from: dto, scaleId: device.id)
            clonedPreference.isSynced = preference.isSynced
            copy.r4ScalePreference = clonedPreference
        }
        if let metaData = device.metaData {
            copy.metaData = cloneMetaData(metaData)
        }
        return copy
    }

    private func cloneMetaData(_ metaData: DeviceMetaData) -> DeviceMetaData {
        let clonedMetaData = DeviceMetaData(
            modelNumber: metaData.modelNumber,
            serialNumber: metaData.serialNumber,
            firmwareRevision: metaData.firmwareRevision,
            hardwareRevision: metaData.hardwareRevision,
            softwareRevision: metaData.softwareRevision,
            manufacturerName: metaData.manufacturerName,
            systemId: metaData.systemId,
            latestVersion: metaData.latestVersion
        )
        clonedMetaData.isSynced = metaData.isSynced
        return clonedMetaData
    }
}
