import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct LoggerApiRepositoryAPITests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: LoggerApiRepository, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = LoggerApiRepository(httpClient: http)
        return (sut, http)
    }

    // MARK: - Fixtures

    private func makePayload(version: String = "1.0.0", logs: [LogEntryPayload] = []) -> LogsPayload {
        LogsPayload(version: version, logs: logs)
    }

    private func makeLogEntry(time: String = "2026-03-11T12:00:00Z", message: String = "tag: message") -> LogEntryPayload {
        LogEntryPayload(time: time, data: .string(message))
    }

    // MARK: - Request construction & endpoint selection

    @Test("sendLogs success: calls send with .log endpoint, POST, needsAuth true")
    func sendLogsSuccessRequestConstruction() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""

        try await sut.sendLogs(makePayload())

        #expect(http.sendCalls == 1)
        guard case .log = http.lastSendEndpoint else {
            Issue.record("Expected .log endpoint"); return
        }
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
    }

    @Test("sendLogs endpoint: uses support/log path (contract)")
    func sendLogsEndpointContract() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""

        try await sut.sendLogs(makePayload())

        guard case .log = http.lastSendEndpoint else {
            Issue.record("Expected .log endpoint (support/log)"); return
        }
    }

    // MARK: - Payload encoding

    @Test("sendLogs payload: body is same LogsPayload sent to client")
    func sendLogsPayloadEncoding() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""
        let payload = makePayload(version: "2.0.0", logs: [
            makeLogEntry(time: "2026-03-11T10:00:00Z", message: "TestTag: test message")
        ])

        try await sut.sendLogs(payload)

        #expect(http.sendCalls == 1)
        guard let body = http.lastSendBody as? LogsPayload else {
            Issue.record("Expected LogsPayload body"); return
        }
        #expect(body.version == "2.0.0")
        #expect(body.logs.count == 1)
        #expect(body.logs[0].time == "2026-03-11T10:00:00Z")
        if case .string(let msg) = body.logs[0].data {
            #expect(msg == "TestTag: test message")
        } else {
            Issue.record("Expected .string log data")
        }
    }

    @Test("sendLogs empty logs: payload with empty logs array is sent")
    func sendLogsEmptyPayload() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""
        let payload = makePayload(version: "1.0", logs: [])

        try await sut.sendLogs(payload)

        #expect(http.sendCalls == 1)
        guard let body = http.lastSendBody as? LogsPayload else {
            Issue.record("Expected LogsPayload body"); return
        }
        #expect(body.logs.isEmpty)
        #expect(body.version == "1.0")
    }

    @Test("sendLogs multiple entries: payload with multiple LogEntryPayload is sent")
    func sendLogsMultipleEntries() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""
        let payload = makePayload(logs: [
            makeLogEntry(message: "A: first"),
            makeLogEntry(time: "2026-03-11T13:00:00Z", message: "B: second")
        ])

        try await sut.sendLogs(payload)

        guard let body = http.lastSendBody as? LogsPayload else {
            Issue.record("Expected LogsPayload body"); return
        }
        #expect(body.logs.count == 2)
        if case .string(let firstMessage) = body.logs[0].data {
            #expect(firstMessage == "A: first")
        } else {
            Issue.record("Expected first entry string")
        }
        if case .string(let secondMessage) = body.logs[1].data {
            #expect(secondMessage == "B: second")
        } else {
            Issue.record("Expected second entry string")
        }
    }

    // MARK: - Response handling & success

    @Test("sendLogs success: does not throw when client returns String response")
    func sendLogsSuccessNoThrow() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""

        try await sut.sendLogs(makePayload())

        #expect(http.sendCalls == 1)
    }

    @Test("sendLogs success: accepts any String response body (contract)")
    func sendLogsSuccessAcceptsStringResponse() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = "ok"

        try await sut.sendLogs(makePayload())

        #expect(http.sendCalls == 1)
    }

    // MARK: - Error propagation & status mapping

    @Test("sendLogs failure: propagates serverError from http client")
    func sendLogsFailureServerError() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.sendLogs(makePayload())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("sendLogs failure: propagates unauthorized from http client")
    func sendLogsFailureUnauthorized() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.sendLogs(makePayload())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("sendLogs failure: propagates noInternet from http client")
    func sendLogsFailureNoInternet() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.sendLogs(makePayload())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("sendLogs failure: propagates timeout from http client")
    func sendLogsFailureTimeout() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.timeout

        await #expect(throws: HTTPError.timeout) {
            try await sut.sendLogs(makePayload())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("sendLogs failure: propagates notFound from http client")
    func sendLogsFailureNotFound() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.notFound

        await #expect(throws: HTTPError.notFound) {
            try await sut.sendLogs(makePayload())
        }
    }

    @Test("sendLogs failure: propagates apiError from http client")
    func sendLogsFailureApiError() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.apiError(message: "Bad request", code: 400)

        await #expect(throws: HTTPError.apiError(message: "Bad request", code: 400)) {
            try await sut.sendLogs(makePayload())
        }
        #expect(http.sendCalls == 1)
    }

    @Test("sendLogs failure: propagates decodingError from http client")
    func sendLogsFailureDecodingError() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.decodingError

        await #expect(throws: HTTPError.decodingError) {
            try await sut.sendLogs(makePayload())
        }
        #expect(http.sendCalls == 1)
    }

    // MARK: - Repeated calls / contract stability

    @Test("sendLogs repeated calls: second call succeeds (idempotent contract)")
    func sendLogsRepeatedCalls() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""

        try await sut.sendLogs(makePayload())
        try await sut.sendLogs(makePayload(logs: [makeLogEntry(message: "second")]))

        #expect(http.sendCalls == 2)
        guard case .log = http.lastSendEndpoint else {
            Issue.record("Expected .log endpoint"); return
        }
        guard let body = http.lastSendBody as? LogsPayload else {
            Issue.record("Expected LogsPayload on second call"); return
        }
        #expect(body.logs.count == 1)
    }

    @Test("sendLogs after failure: next call still uses correct endpoint and body")
    func sendLogsAfterFailure() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.sendLogs(makePayload())
        }
        #expect(http.sendCalls == 1)

        http.sendError = nil
        http.sendResult = ""

        try await sut.sendLogs(makePayload(version: "retry"))

        #expect(http.sendCalls == 2)
        guard case .log = http.lastSendEndpoint else {
            Issue.record("Expected .log endpoint on retry"); return
        }
        guard let body = http.lastSendBody as? LogsPayload else {
            Issue.record("Expected LogsPayload on retry"); return
        }
        #expect(body.version == "retry")
    }

    // MARK: - Malformed / edge payload behavior

    @Test("sendLogs edge payload: long version string is passed through")
    func sendLogsEdgeLongVersion() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""
        let longVersion = String(repeating: "v", count: 500)
        let payload = makePayload(version: longVersion, logs: [])

        try await sut.sendLogs(payload)

        guard let body = http.lastSendBody as? LogsPayload else {
            Issue.record("Expected LogsPayload"); return
        }
        #expect(body.version == longVersion)
    }

    @Test("sendLogs edge payload: log entry with array data is passed through")
    func sendLogsEdgeArrayData() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = ""
        let entry = LogEntryPayload(time: "2026-03-11T12:00:00Z", data: .array("message", ["extra"]))
        let payload = makePayload(logs: [entry])

        try await sut.sendLogs(payload)

        guard let body = http.lastSendBody as? LogsPayload else {
            Issue.record("Expected LogsPayload"); return
        }
        #expect(body.logs.count == 1)
        if case .array(let msg, let data) = body.logs[0].data {
            #expect(msg == "message")
            #expect(data?.isEmpty == false)
        } else {
            Issue.record("Expected .array log data")
        }
    }
}
