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
/// All operations are serialized per device to avoid SDK callback collisions.
/// If the SDK supports independent callbacks per operation type, this can be relaxed.
actor SDKOperationSerializer {
    private var lastTask: [String: Task<Void, Never>] = [:]
    
    /// Executes an SDK operation, serializing calls per device to avoid SDK callback collisions.
    /// Operations for the same device are queued and executed sequentially, ensuring each operation
    /// runs with its own inputs and produces its own result.
    /// - Parameters:
    ///   - operationKey: Key combining device ID and operation type (e.g., "deviceId:updateAccount")
    ///   - operation: The async operation to execute (must be @MainActor)
    /// - Returns: The result of the operation
    func execute<T: Sendable>(
        operationKey: String,
        operation: @escaping @MainActor () async throws -> T
    ) async throws -> T {
        let queueKey = operationKey.split(separator: ":", maxSplits: 1).first.map(String.init) ?? operationKey

        // Wait for the previous task for this device to complete
        if let previousTask = lastTask[queueKey] {
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
            lastTask[queueKey] = operationTask
        }
    }
}
