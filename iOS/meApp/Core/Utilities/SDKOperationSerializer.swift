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
/// This actor ensures only one operation of the same type happens at a time per device, with subsequent calls
/// waiting for the previous one to complete and returning the same result.
/// 
/// Different operation types can run concurrently for the same device (e.g., updateAccount and getUsers),
/// but the same operation type is serialized per device.
actor SDKOperationSerializer {
    private var activeTasks: [String: Task<Any, Error>] = [:]
    
    /// Executes an SDK operation, serializing calls per operation key (deviceId:operationType).
    /// If a call is already in progress for this operation key, waits for it to complete and returns its result.
    /// - Parameters:
    ///   - operationKey: Unique key combining device ID and operation type (e.g., "deviceId:updateAccount")
    ///   - operation: The async operation to execute (must be @MainActor)
    /// - Returns: The result of the operation
    func execute<T: Sendable>(
        operationKey: String,
        operation: @escaping @MainActor () async throws -> T
    ) async throws -> T {
        // If there's already a task for this operation key, wait for it to complete
        if let existingTask = activeTasks[operationKey] {
            // Check if task was cancelled, remove it and start fresh
            if existingTask.isCancelled {
                activeTasks.removeValue(forKey: operationKey)
            } else {
                // Cast the result back to the expected type
                let result = try await existingTask.value
                // Remove synchronously after completion to prevent race conditions
                activeTasks.removeValue(forKey: operationKey)
                guard let typedResult = result as? T else {
                    throw NSError(domain: "SDKOperationSerializer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Type mismatch in serialized operation result"])
                }
                return typedResult
            }
        }
        
        // Create new task for this operation key
        let task = Task<Any, Error> { @MainActor in
            return try await operation()
        }
        
        activeTasks[operationKey] = task
        
        // Await the task and ensure cleanup happens synchronously
        do {
            let result = try await task.value
            // Remove synchronously after completion to prevent race conditions
            activeTasks.removeValue(forKey: operationKey)
            guard let typedResult = result as? T else {
                throw NSError(domain: "SDKOperationSerializer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Type mismatch in serialized operation result"])
            }
            return typedResult
        } catch {
            // Always remove on error to prevent memory leaks
            activeTasks.removeValue(forKey: operationKey)
            throw error
        }
    }
}
