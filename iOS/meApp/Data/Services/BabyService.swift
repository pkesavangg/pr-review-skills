//
//  BabyService.swift
//  meApp
//

import Combine
import Foundation
import SwiftData

/// Concrete implementation of BabyServiceProtocol.
///
/// Backs the local SwiftData `Baby` table with the remote Baby Profile API (MOB-386).
///
/// Offline-first (MOB-1527): create / edit / delete write to SwiftData **first** (unsynced) and
/// never throw when offline — the baby appears in My Kids / dashboard immediately and `productTypes`
/// reflects `"baby"` locally. The queued change is reconciled by `syncBabies(for:)` on reconnect,
/// on the next full sync (it runs ahead of entry sync), and eagerly right after each mutation.
///
/// The pending server call is inferred from the record's flags (see `Baby`):
/// - never-synced create → `POST /v3/baby/` then the client id is remapped to the server id
///   everywhere (profile, baby entries, active-baby pointer);
/// - server-side edit → `PUT /v3/baby/{id}`;
/// - server-side delete → `DELETE /v3/baby/{id}` then purge; created-then-deleted-offline purges
///   locally with no server call.
///
/// ProductTypes are kept in sync — `"baby"` is appended on first create and stripped once the last
/// (non-tombstoned) baby profile is deleted.
@MainActor
final class BabyService: ObservableObject, BabyServiceProtocol {
    static let shared = BabyService()

    @Injector private var accountService: AccountServiceProtocol

    @Published var babies: [Baby] = []

    var babiesPublisher: Published<[Baby]>.Publisher { $babies }
    var currentBabies: [Baby] { babies }

    private let context = PersistenceController.shared.context
    private let remoteRepo: BabyRepositoryAPIProtocol
    private let kvStorage: KvStorageServiceProtocol
    /// Connectivity check, injectable so tests can drive online/offline deterministically instead
    /// of depending on the real `NetworkMonitor.shared` singleton.
    private let isConnected: @MainActor () -> Bool
    private let tag = "BabyService"

    /// Abandon a record's pending push after this many non-network failures (mirrors EntryService).
    private static let maxSyncAttempts = 8

    /// Guards against concurrent `syncBabies` runs — later callers await the in-flight task.
    private var activeSyncTask: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()

    /// Resolved lazily and non-fatally so unit tests that construct `BabyService` without an
    /// `EntryService` registered still work — the baby-entry FK remap is best-effort.
    private var entryService: EntryServiceProtocol? {
        DependencyContainer.shared.resolve(EntryServiceProtocol.self)
    }

    init(
        remoteRepo: BabyRepositoryAPIProtocol? = nil,
        kvStorage: KvStorageServiceProtocol? = nil,
        isConnected: (@MainActor () -> Bool)? = nil,
        reconnectSignal: AnyPublisher<Bool, Never>? = nil
    ) {
        self.remoteRepo = remoteRepo ?? BabyRepositoryAPI()
        self.kvStorage = kvStorage ?? KvStorageService.shared
        self.isConnected = isConnected ?? { NetworkMonitor.shared.isConnected }
        subscribeToReconnect(reconnectSignal ?? NetworkMonitor.shared.isConnectedPublisher)
    }

    /// Flushes the queued baby changes whenever connectivity is restored, mirroring the intent of
    /// the entry offline-sync path (which relies on foreground/account-change re-triggers).
    private func subscribeToReconnect(_ signal: AnyPublisher<Bool, Never>) {
        signal
            .dropFirst() // ignore the current-value replay on subscribe
            .removeDuplicates()
            .filter { $0 } // only on transition to connected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, let accountId = self.accountService.activeAccount?.accountId else { return }
                    await self.syncBabies(for: accountId)
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Local-first Mutations

    // swiftlint:disable:next function_parameter_count
    func saveBaby(
        name: String,
        accountId: String,
        deviceId: String?,
        birthday: Date?,
        biologicalSex: String?,
        birthLengthInches: Double?,
        birthWeightLbs: Double?,
        birthWeightOz: Double?
    ) async throws -> Baby {
        // Local-first: insert an unsynced record so the baby appears immediately, even offline.
        // The server id is assigned later by `syncBabies` (POST) and remapped over this client id.
        let baby = Baby(
            accountId: accountId,
            name: name,
            deviceId: deviceId,
            isSynced: false,
            isServerCreated: false,
            birthday: birthday,
            biologicalSex: biologicalSex,
            birthLengthInches: birthLengthInches,
            birthWeightLbs: birthWeightLbs,
            birthWeightOz: birthWeightOz
        )
        context.insert(baby)
        try context.save()
        try reloadLocalBabies(for: accountId)
        // Best-effort: the baby product type also surfaces locally via ProductTypeStore's union
        // from `currentBabies`, and reconciles on reconnect — so an offline PATCH failure here must
        // not fail the create (MOB-1527: creating a baby offline shows no error).
        try? await appendBabyProductTypeIfNeeded()
        logger(.info, "Baby created locally (unsynced): id=\(baby.id), accountId=\(accountId)")
        triggerEagerSync(for: accountId)
        return baby
    }

    func updateBaby(_ baby: Baby, name: String) async throws {
        baby.name = name
        markPendingEdit(baby)
        try context.save()
        try reloadLocalBabies(for: baby.accountId)
        triggerEagerSync(for: baby.accountId)
    }

    // swiftlint:disable:next function_parameter_count
    func updateBabyProfile(
        _ baby: Baby,
        name: String,
        birthday: Date?,
        biologicalSex: String?,
        birthLengthInches: Double?,
        birthWeightLbs: Double?,
        birthWeightOz: Double?
    ) async throws {
        baby.name = name
        baby.birthday = birthday
        baby.biologicalSex = biologicalSex
        baby.birthLengthInches = birthLengthInches
        baby.birthWeightLbs = birthWeightLbs
        baby.birthWeightOz = birthWeightOz
        markPendingEdit(baby)
        try context.save()
        try reloadLocalBabies(for: baby.accountId)
        triggerEagerSync(for: baby.accountId)
    }

    func deleteBaby(_ baby: Baby) async throws {
        let accountId = baby.accountId
        if baby.isServerCreated {
            // Already on the server — tombstone it so the DELETE can be pushed on reconnect.
            // Keeping the row (not `context.delete`) also avoids any risk of cascading into its
            // baby entries; on iOS `Baby` has no relationship to entries, but the tombstone keeps
            // the pending-delete intent regardless (MOB-1527 / cf. MOB-598 on Android).
            baby.isDeleted = true
            baby.isSynced = false
            baby.attempts = 0
            baby.isFailedToSync = false
            logger(.info, "Baby delete queued (tombstone): id=\(baby.id), accountId=\(accountId)")
        } else {
            // Never reached the server (created-then-deleted offline) — drop the pending create.
            context.delete(baby)
            logger(.info, "Baby create discarded before sync: id=\(baby.id), accountId=\(accountId)")
        }
        try context.save()
        try reloadLocalBabies(for: accountId)
        // Best-effort (see saveBaby): an offline PATCH failure must not fail the delete.
        try? await removeBabyProductTypeIfLast()
        triggerEagerSync(for: accountId)
    }

    /// Marks an existing record as having pending local edits to push. Leaves `isServerCreated`
    /// untouched: an offline-created baby edited before its first sync is still a pending POST.
    private func markPendingEdit(_ baby: Baby) {
        baby.isSynced = false
        baby.isDeleted = false
        baby.attempts = 0
        baby.isFailedToSync = false
    }

    // MARK: - Load / Sync

    func loadBabies(for accountId: String) async throws {
        // A load doubles as a reconcile so queued changes flush and the server list merges in.
        await syncBabies(for: accountId)
    }

    func syncBabies(for accountId: String) async {
        if let existing = activeSyncTask {
            await existing.value
            return
        }
        let task = Task { [weak self] in
            guard let self else { return }
            await self.performBabySync(for: accountId)
        }
        activeSyncTask = task
        await task.value
        activeSyncTask = nil
    }

    /// Fires a best-effort reconcile right after a local mutation so a just-made change reaches the
    /// server without waiting for the next foreground/dashboard sync. No-op when offline — the
    /// change stays queued for the reconnect subscription or the next full sync.
    private func triggerEagerSync(for accountId: String) {
        guard isConnected() else { return }
        Task { [weak self] in
            await self?.syncBabies(for: accountId)
        }
    }

    /// The actual reconcile: push queued deletes, then creates (with id remap + lost-reply dedupe),
    /// then edits, then merge the fresh server list. Best-effort — offline / transient failures
    /// leave records queued and simply fall back to the local cache.
    private func performBabySync(for accountId: String) async {
        guard isConnected() else {
            logger(.info, "Baby sync skipped — offline; records remain queued for accountId=\(accountId)")
            try? reloadLocalBabies(for: accountId)
            return
        }

        // Snapshot the server list first — needed to dedupe pending creates against a baby that a
        // prior POST created but whose reply was lost (adopt it instead of POSTing a duplicate).
        let serverListBeforePush: [BabyResponse]
        do {
            serverListBeforePush = try await remoteRepo.listBabies()
        } catch {
            logger(.error, "Baby sync — failed to fetch server list for accountId=\(accountId): \(error.localizedDescription)")
            try? reloadLocalBabies(for: accountId)
            return
        }

        await pushPendingDeletes(for: accountId)
        let protectedIds = await pushPendingCreates(for: accountId, serverList: serverListBeforePush)
        await pushPendingEdits(for: accountId)

        // Merge the latest server list; protect the ids we just POSTed this pass (they aren't in the
        // pre-push snapshot yet, so the stale-delete sweep would otherwise wipe them).
        do {
            let serverListAfterPush = try await remoteRepo.listBabies()
            try mergeRemoteBabies(serverListAfterPush, accountId: accountId, protectedIds: protectedIds)
        } catch {
            logger(.error, "Baby sync — merge fetch failed for accountId=\(accountId): \(error.localizedDescription)")
        }

        try? reloadLocalBabies(for: accountId)
        await reconcileBabyProductType()
    }

    // MARK: - Push: Delete

    private func pushPendingDeletes(for accountId: String) async {
        let predicate = #Predicate<Baby> { $0.accountId == accountId && $0.isDeleted == true && $0.isSynced == false }
        let pending = (try? context.fetch(FetchDescriptor<Baby>(predicate: predicate))) ?? []
        for baby in pending {
            if baby.isServerCreated {
                do {
                    try await remoteRepo.deleteBaby(baby.id)
                    context.delete(baby)
                    try? context.save()
                    logger(.info, "Baby delete synced + purged: id=\(baby.id)")
                } catch {
                    if HTTPError.isNetworkError(error) {
                        logger(.info, "Baby delete deferred — offline: id=\(baby.id)")
                    } else if (error as? HTTPError) == .notFound {
                        // Already gone server-side — treat as success and purge locally.
                        context.delete(baby)
                        try? context.save()
                        logger(.info, "Baby delete — already absent server-side, purged: id=\(baby.id)")
                    } else {
                        recordFailure(on: baby, error: error, op: "delete")
                    }
                }
            } else {
                // Defensive: a tombstone that never reached the server is just purged.
                context.delete(baby)
                try? context.save()
            }
        }
    }

    // MARK: - Push: Create

    /// POSTs (or adopts) every never-synced baby and remaps its client id to the server id.
    /// - Returns: the set of server ids created/adopted this pass, so the caller can protect them
    ///   from the merge stale-delete sweep.
    private func pushPendingCreates(for accountId: String, serverList: [BabyResponse]) async -> Set<String> {
        let predicate = #Predicate<Baby> {
            $0.accountId == accountId && $0.isServerCreated == false && $0.isDeleted == false && $0.isSynced == false
        }
        let pending = (try? context.fetch(FetchDescriptor<Baby>(predicate: predicate))) ?? []
        var protectedIds = Set<String>()

        for baby in pending {
            let clientId = baby.id
            // Lost-reply dedupe: if the server already has a matching baby (same name + birthdate)
            // adopt its id rather than POSTing a duplicate.
            if let match = serverList.first(where: { isSameBaby($0, as: baby) }) {
                await remapBaby(from: clientId, to: match.id, accountId: accountId, serverFields: match)
                protectedIds.insert(match.id)
                logger(.info, "Baby create deduped — adopted existing server id: \(clientId) -> \(match.id)")
                continue
            }
            do {
                let response = try await remoteRepo.createBaby(makeRequest(from: baby))
                await remapBaby(from: clientId, to: response.id, accountId: accountId, serverFields: response)
                protectedIds.insert(response.id)
                logger(.info, "Baby create synced + remapped: \(clientId) -> \(response.id)")
            } catch {
                if HTTPError.isNetworkError(error) {
                    logger(.info, "Baby create deferred — offline: id=\(clientId)")
                } else {
                    recordFailure(on: baby, error: error, op: "create")
                }
            }
        }
        return protectedIds
    }

    // MARK: - Push: Edit

    private func pushPendingEdits(for accountId: String) async {
        let predicate = #Predicate<Baby> {
            $0.accountId == accountId && $0.isServerCreated == true && $0.isDeleted == false && $0.isSynced == false
        }
        let pending = (try? context.fetch(FetchDescriptor<Baby>(predicate: predicate))) ?? []
        for baby in pending {
            do {
                _ = try await remoteRepo.updateBaby(baby.id, makeRequest(from: baby))
                baby.isSynced = true
                baby.attempts = 0
                baby.isFailedToSync = false
                try? context.save()
                logger(.info, "Baby edit synced: id=\(baby.id)")
            } catch {
                if HTTPError.isNetworkError(error) {
                    logger(.info, "Baby edit deferred — offline: id=\(baby.id)")
                } else {
                    recordFailure(on: baby, error: error, op: "edit")
                }
            }
        }
    }

    // MARK: - Id Remapping

    /// Replaces an offline-created baby's client id with its server id everywhere: the baby entries
    /// that reference it, the persisted active-baby pointer, and the baby record itself. Done as a
    /// re-point (never a delete-then-recreate that could cascade), so baby entries created offline
    /// against the client id stay attached (MOB-1527).
    private func remapBaby(from clientId: String, to serverId: String, accountId: String, serverFields: BabyResponse?) async {
        guard clientId != serverId else {
            // Same id (unusual) — just mark the existing record synced.
            if let baby = try? fetchLocalBaby(byId: clientId) {
                markServerSynced(baby, serverFields: serverFields)
                try? context.save()
            }
            return
        }

        // 1. Re-point baby entries (weight/length) that were created against the client id.
        await entryService?.remapBabyId(from: clientId, to: serverId)

        // 2. Re-point the persisted active-baby selection.
        remapSelectionPointer(accountId: accountId, from: clientId, to: serverId)

        // 3. Re-point the baby record. If a row with the server id already exists locally (e.g. a
        //    prior merge inserted it), keep that row and purge the client row; otherwise reassign.
        if let existing = try? fetchLocalBaby(byId: serverId) {
            markServerSynced(existing, serverFields: serverFields)
            if let client = try? fetchLocalBaby(byId: clientId) {
                context.delete(client)
            }
        } else if let client = try? fetchLocalBaby(byId: clientId) {
            client.id = serverId
            markServerSynced(client, serverFields: serverFields)
        }
        try? context.save()
    }

    /// Marks a record as present-and-synced on the server, adopting the server's canonical fields
    /// when provided (used for the lost-reply adopt path).
    private func markServerSynced(_ baby: Baby, serverFields: BabyResponse?) {
        baby.isServerCreated = true
        baby.isSynced = true
        baby.isDeleted = false
        baby.attempts = 0
        baby.isFailedToSync = false
        if let fields = serverFields {
            baby.name = fields.name
            baby.birthday = fields.birthdayDate
            baby.biologicalSex = fields.sex
            baby.birthLengthInches = fields.birthLengthInchesValue
            if let weight = fields.birthWeightLbsOz {
                baby.birthWeightLbs = weight.lbs
                baby.birthWeightOz = weight.oz
            }
        }
    }

    /// Rewrites the account-scoped active-product selection when it points at the remapped baby.
    /// The stored value is the `ProductSelection.id` ("baby_<id>").
    private func remapSelectionPointer(accountId: String, from clientId: String, to serverId: String) {
        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        guard let current = kvStorage.getValue(forKey: key) as? String, current == "baby_\(clientId)" else { return }
        kvStorage.setValue("baby_\(serverId)", forKey: key)
        logger(.info, "Remapped active-baby selection: baby_\(clientId) -> baby_\(serverId)")
    }

    // MARK: - Merge

    /// Upserts server baby profiles into the local store, keyed by id, then removes local babies
    /// that are no longer on the server — while preserving (a) not-yet-synced offline creates,
    /// (b) pending delete tombstones, (c) records just created this sync pass (`protectedIds`), and
    /// without clobbering locally-edited-but-unsynced records.
    private func mergeRemoteBabies(_ responses: [BabyResponse], accountId: String, protectedIds: Set<String>) throws {
        let remoteIds = Set(responses.map { $0.id })
        for response in responses {
            if let existing = try fetchLocalBaby(byId: response.id, accountId: accountId) {
                // Don't overwrite a record with pending local changes (edit) or a delete tombstone.
                guard existing.isSynced, !existing.isDeleted else { continue }
                markServerSynced(existing, serverFields: response)
            } else {
                context.insert(response.toBaby(accountId: accountId))
            }
        }

        let locals = try fetchAllBabies(for: accountId)
        for baby in locals where !remoteIds.contains(baby.id) {
            if !baby.isServerCreated { continue }           // offline create not pushed yet
            if baby.isDeleted && !baby.isSynced { continue } // pending delete tombstone
            if protectedIds.contains(baby.id) { continue }   // created this pass, not in the snapshot
            context.delete(baby)
        }
        try context.save()
    }

    // MARK: - Local Store Helpers

    /// Reloads the published `babies` from the local store for the given account (no network),
    /// excluding delete tombstones so the UI never shows a baby pending deletion.
    private func reloadLocalBabies(for accountId: String) throws {
        let descriptor = FetchDescriptor<Baby>(
            predicate: #Predicate<Baby> { $0.accountId == accountId && $0.isDeleted == false },
            sortBy: [SortDescriptor(\.name)]
        )
        babies = try context.fetch(descriptor)
    }

    /// Fetches all babies for an account, INCLUDING delete tombstones.
    private func fetchAllBabies(for accountId: String) throws -> [Baby] {
        try context.fetch(FetchDescriptor<Baby>(predicate: #Predicate<Baby> { $0.accountId == accountId }))
    }

    private func fetchLocalBaby(byId id: String, accountId: String? = nil) throws -> Baby? {
        let descriptor: FetchDescriptor<Baby>
        if let accountId {
            descriptor = FetchDescriptor<Baby>(predicate: #Predicate { $0.id == id && $0.accountId == accountId })
        } else {
            descriptor = FetchDescriptor<Baby>(predicate: #Predicate { $0.id == id })
        }
        return try context.fetch(descriptor).first
    }

    /// True when a server baby is the same profile as a pending local create (lost-reply dedupe).
    /// Matches on name (case-insensitive) plus birthdate.
    private func isSameBaby(_ response: BabyResponse, as baby: Baby) -> Bool {
        let sameName = response.name.caseInsensitiveCompare(baby.name) == .orderedSame
        return sameName && response.birthdayDate == baby.birthday
    }

    private func makeRequest(from baby: Baby) -> BabyRequest {
        BabyRequest(
            name: baby.name,
            birthday: baby.birthday,
            biologicalSex: baby.biologicalSex,
            birthLengthInches: baby.birthLengthInches,
            birthWeightLbs: baby.birthWeightLbs,
            birthWeightOz: baby.birthWeightOz
        )
    }

    /// Increments the retry counter for a non-network failure and abandons the record's pending
    /// push once the cap is exceeded (marking it synced so it stops retrying), mirroring EntryService.
    private func recordFailure(on baby: Baby, error: Error, op: String) {
        baby.attempts += 1
        if baby.attempts > Self.maxSyncAttempts {
            baby.isFailedToSync = true
            baby.isSynced = true
            logger(.error, "Baby \(op) abandoned after \(baby.attempts) attempts: id=\(baby.id), error=\(error.localizedDescription)")
        } else {
            logger(.error, "Baby \(op) failed (attempt \(baby.attempts)): id=\(baby.id), error=\(error.localizedDescription)")
        }
        try? context.save()
    }

    // MARK: - ProductTypes Sync

    /// Reconciles the account's baby product type against the final local state after a sync.
    private func reconcileBabyProductType() async {
        if babies.isEmpty {
            try? await removeBabyProductTypeIfLast()
        } else {
            try? await appendBabyProductTypeIfNeeded()
        }
    }

    /// Appends "baby" to the active account's productTypes if not already present.
    private func appendBabyProductTypeIfNeeded() async throws {
        guard let snapshot = accountService.activeAccount,
              !snapshot.productTypes.contains("baby") else { return }
        try await accountService.updateProductTypes(snapshot.productTypes + ["baby"])
        logger(.info, "Appended baby to productTypes for accountId=\(snapshot.accountId)")
    }

    /// Removes "baby" from the active account's productTypes once no baby profiles remain.
    private func removeBabyProductTypeIfLast() async throws {
        guard babies.isEmpty,
              let snapshot = accountService.activeAccount,
              snapshot.productTypes.contains("baby") else { return }
        // Use the dedicated reducing path: updateProductTypes(_:) never reduces (it unions
        // with the existing local value), so filtering "baby" out and sending it there would
        // be a no-op that re-adds "baby". removeProductType authoritatively drops it.
        try await accountService.removeProductType("baby")
        logger(.info, "Removed baby from productTypes for accountId=\(snapshot.accountId)")
    }

    // MARK: - Logging

    private func logger(_ level: LogLevel, _ message: String) {
        LoggerService.shared.log(level: level, tag: tag, message: message)
    }
}
