//
//  SDKOperationSerializer.swift
//  meApp
//
//  Created by AI Assistant
//
//  Generic actor that serializes SDK operations per device+operation to prevent callback conflicts.
//  The SDK only maintains one completion handler per operation type at a time, so concurrent calls overwrite each other.
//

import Foundation

/// Generic actor that serializes SDK operations per device+operation to prevent callback conflicts.
/// The SDK only maintains one completion handler per operation type at a time, so concurrent calls overwrite each other.
/// This actor ensures operations of the same type are executed sequentially per device, with each operation
/// running after the previous one completes (true serialization).
/// 
/// Different operation types can run concurrently for the same device (e.g., updateAccount and getUsers),
/// but the same operation type is serialized per device.
actor SDKOperationSerializer {
    private var lastTask: [String: Task<Void, Never>] = [:]
    
    /// Executes an SDK operation, serializing calls per operation key (deviceId:operationType).
    /// Operations with the same key are queued and executed sequentially, ensuring each operation
    /// runs with its own inputs and produces its own result.
    /// - Parameters:
    ///   - operationKey: Unique key combining device ID and operation type (e.g., "deviceId:updateAccount")
    ///   - operation: The async operation to execute (must be @MainActor)
    /// - Returns: The result of the operation
    func execute<T: Sendable>(
        operationKey: String,
        operation: @escaping @MainActor () async throws -> T
    ) async throws -> T {
        // Wait for the previous task for this operation key to complete
        if let previousTask = lastTask[operationKey] {
            await previousTask.value
        }
        
        // Create a task that executes the operation and captures its completion
        // We use a continuation to bridge the task execution with the result
        return try await withCheckedThrowingContinuation { continuation in
            // Create the task that will execute the operation
            let operationTask = Task<Void, Never> { @MainActor [operation] in
                do {
                    let result = try await operation()
                    continuation.resume(returning: result)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
            
            // Store the task so the next operation waits for it
            // Old tasks will be naturally replaced when new operations start
            lastTask[operationKey] = operationTask
        }
    }
}
