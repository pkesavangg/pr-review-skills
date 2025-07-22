//
//  LoggerApiRepositoryProtocol.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 21/07/25.
//
import Foundation

/// Protocol for logger API operations
protocol LoggerApiRepositoryProtocol {
    func sendLogs(_ logsPayload: LogsPayload) async throws
}
