//
//  MetricKitService.swift
//  meApp
//
//  Created for MOB-520 — production performance telemetry.
//

import Foundation
import MetricKit

/// Subscribes to MetricKit and forwards the OS-collected performance metrics and
/// diagnostics (hang / CPU-exception / crash / disk-write-exception stacks) to the
/// app logger, so the 5.1.0 performance hangs can be ranked from real field data
/// instead of one-off local Instruments traces. (MOB-520)
///
/// MetricKit is **passive**: iOS gathers the data; this service only *receives* it.
/// Delivery is **batched and delayed** — payloads arrive aggregated (roughly daily,
/// usually on a later launch), never in real time — so the value here is fleet-wide
/// statistics, not live crash reporting. Overhead is negligible; payloads carry
/// call-stack trees and counters, with no PII / no health data.
///
/// The subscriber must be registered at launch (via `ServiceRegistry`) because MetricKit
/// delivers the *previous* launch's payloads shortly after startup.
final class MetricKitService: NSObject, MXMetricManagerSubscriber, @unchecked Sendable {
    static let shared = MetricKitService()

    private static let tag = "MetricKitService"

    /// Sink for a serialized payload `(level, message, json)`. Injected so tests can assert
    /// forwarding without constructing MetricKit payloads (which have no public initializers).
    /// The default hops to the main actor and logs via `LoggerService`.
    private let logHandler: @Sendable (LogLevel, String, String) -> Void

    init(logHandler: (@Sendable (LogLevel, String, String) -> Void)? = nil) {
        self.logHandler = logHandler ?? { level, message, json in
            Task { @MainActor in
                LoggerService.shared.log(level: level, tag: MetricKitService.tag, message: message, data: json)
            }
        }
        super.init()
    }

    /// Registers this subscriber with MetricKit. Call once at launch.
    func start() {
        MXMetricManager.shared.add(self)
    }

    /// Removes this subscriber. Provided for symmetry/tests; a launch-lifetime singleton need not call it.
    func stop() {
        MXMetricManager.shared.remove(self)
    }

    // MARK: - MXMetricManagerSubscriber

    /// Aggregated performance metrics (hang time, CPU, memory, launch time, disk writes). ~once/day.
    func didReceive(_ payloads: [MXMetricPayload]) {
        forward(payloads.map { $0.jsonRepresentation() }, level: .info, kind: "metric")
    }

    /// Diagnostics: hang / CPU-exception / crash / disk-write-exception stacks — the signal this
    /// task exists for. Logged at `.error` (the `LogLevel` enum has no `.warning`) so the stacks
    /// always persist and stand out for field triage.
    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        forward(payloads.map { $0.jsonRepresentation() }, level: .error, kind: "diagnostic")
    }

    // MARK: - Private

    /// Serializes each payload's JSON and hands it to the log sink. This is the unit-testable
    /// seam — the framework `didReceive` glue above cannot be exercised without real (and
    /// un-constructable) MetricKit payloads; verify those on device via Xcode ▸ Debug ▸
    /// "Simulate MetricKit Payloads".
    func forward(_ payloadsJSON: [Data], level: LogLevel, kind: String) {
        for data in payloadsJSON {
            let json = String(bytes: data, encoding: .utf8) ?? ""
            logHandler(level, "MetricKit \(kind) payload received", json)
        }
    }
}
