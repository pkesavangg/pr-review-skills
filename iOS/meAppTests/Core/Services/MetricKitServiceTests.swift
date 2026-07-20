import Foundation
@testable import meApp
import Testing

private struct LoggedEntry: Sendable {
    let level: LogLevel
    let message: String
    let json: String
}

/// Thread-safe sink recorder. `MetricKitService.forward` invokes the log handler
/// synchronously, so entries are populated before the assertion runs.
private final class LogRecorder: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [LoggedEntry] = []

    func record(_ level: LogLevel, _ message: String, _ json: String) {
        lock.lock(); defer { lock.unlock() }
        storage.append(LoggedEntry(level: level, message: message, json: json))
    }

    var entries: [LoggedEntry] {
        lock.lock(); defer { lock.unlock() }
        return storage
    }
}

@Suite(.serialized)
struct MetricKitServiceTests {
    private func makeSUT(_ recorder: LogRecorder) -> MetricKitService {
        MetricKitService { level, message, json in recorder.record(level, message, json) }
    }

    @Test("forward: a diagnostic payload logs at .error with the serialized JSON")
    func forwardDiagnosticLogsError() throws {
        let recorder = LogRecorder()
        let sut = makeSUT(recorder)
        let json = #"{"hangDiagnostics":"stack"}"#

        sut.forward([Data(json.utf8)], level: .error, kind: "diagnostic")

        #expect(recorder.entries.count == 1)
        let entry = try #require(recorder.entries.first)
        #expect(entry.level == .error)
        #expect(entry.message == "MetricKit diagnostic payload received")
        #expect(entry.json == json)
    }

    @Test("forward: a metric payload logs at .info")
    func forwardMetricLogsInfo() throws {
        let recorder = LogRecorder()
        let sut = makeSUT(recorder)

        sut.forward([Data(#"{"cpuMetrics":{}}"#.utf8)], level: .info, kind: "metric")

        let entry = try #require(recorder.entries.first)
        #expect(entry.level == .info)
        #expect(entry.message == "MetricKit metric payload received")
    }

    @Test("forward: emits one log entry per payload")
    func forwardEmitsPerPayload() {
        let recorder = LogRecorder()
        let sut = makeSUT(recorder)

        sut.forward(
            [Data("a".utf8), Data("b".utf8), Data("c".utf8)],
            level: .error,
            kind: "diagnostic"
        )

        #expect(recorder.entries.count == 3)
        #expect(recorder.entries.map(\.json) == ["a", "b", "c"])
    }

    @Test("forward: no payloads logs nothing")
    func forwardEmptyLogsNothing() {
        let recorder = LogRecorder()
        let sut = makeSUT(recorder)

        sut.forward([], level: .error, kind: "diagnostic")

        #expect(recorder.entries.isEmpty)
    }
}
