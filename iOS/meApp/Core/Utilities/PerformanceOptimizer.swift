//
//  PerformanceOptimizer.swift
//  meApp
//
//  Created by AI Assistant on 27/01/25.
//  Reusable performance optimization utilities for iOS app
//

import Combine
import Foundation
import os

// MARK: - Performance Optimizer

/// Utility class providing reusable performance optimization patterns
///
/// **Key Features:**
/// - Debouncing for UI updates
/// - Generic caching system
/// - Background processing utilities
/// - Performance monitoring
/// - Memory management helpers
/// - Batch operation support
final class PerformanceOptimizer {

    // MARK: - Shared Instance
    static let shared = PerformanceOptimizer()

    // MARK: - Properties
    private let backgroundQueue = DispatchQueue(label: "PerformanceOptimizer.background", qos: .userInitiated)
    private let perfLog = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "PerformanceOptimizer", category: "Performance")
    private var debouncers: [String: Timer] = [:]
    private var caches: [String: Any] = [:]
    private var performanceTimers: [String: Date] = [:]

    // MARK: - Initialization
    private init() {}

    // MARK: - Debouncing

    /// Debounce function execution to reduce frequency
    /// - Parameters:
    ///   - key: Unique identifier for the debouncer
    ///   - delay: Delay in seconds before execution
    ///   - action: Action to execute after delay
    func debounce(key: String, delay: TimeInterval, action: @escaping () -> Void) {
        // Cancel existing debouncer
        debouncers[key]?.invalidate()

        // Create new debouncer
        debouncers[key] = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
            action()
            self?.debouncers[key] = nil
        }
    }

    /// Debounce async function execution
    /// - Parameters:
    ///   - key: Unique identifier for the debouncer
    ///   - delay: Delay in seconds before execution
    ///   - action: Async action to execute after delay
    func debounceAsync(key: String, delay: TimeInterval, action: @escaping () async -> Void) {
        debounce(key: key, delay: delay) {
            Task {
                await action()
            }
        }
    }

    /// Debounce with MainActor execution
    /// - Parameters:
    ///   - key: Unique identifier for the debouncer
    ///   - delay: Delay in seconds before execution
    ///   - action: MainActor action to execute after delay
    @MainActor
    func debounceOnMain(key: String, delay: TimeInterval, action: @escaping @MainActor () -> Void) {
        debounce(key: key, delay: delay) {
            Task { @MainActor in
                action()
            }
        }
    }

    // MARK: - Caching

    /// Generic cache with automatic cleanup
    private class Cache<T> {
        private var storage: [String: CacheItem<T>] = [:]
        private let maxSize: Int
        private let ttl: TimeInterval

        init(maxSize: Int = 100, ttl: TimeInterval = 300) {
            self.maxSize = maxSize
            self.ttl = ttl
        }

        func get(key: String) -> T? {
            cleanupExpired()
            return storage[key]?.value
        }

        func set(key: String, value: T) {
            storage[key] = CacheItem(value: value, timestamp: Date())

            // Cleanup if over max size
            if storage.count > maxSize {
                let oldestKey = storage.min { $0.value.timestamp < $1.value.timestamp }?.key
                if let oldestKey = oldestKey {
                    storage.removeValue(forKey: oldestKey)
                }
            }
        }

        func remove(key: String) {
            storage.removeValue(forKey: key)
        }

        func removeAll() {
            storage.removeAll()
        }

        private func cleanupExpired() {
            let now = Date()
            let expiredKeys = storage.filter { now.timeIntervalSince($0.value.timestamp) > ttl }.map { $0.key }
            expiredKeys.forEach { storage.removeValue(forKey: $0) }
        }
    }

    private struct CacheItem<T> {
        let value: T
        let timestamp: Date
    }

    /// Get cached value
    /// - Parameters:
    ///   - key: Cache key
    ///   - type: Type of cached value
    /// - Returns: Cached value if available
    func getCachedValue<T>(key: String, type: T.Type) -> T? {
        return (caches[key] as? Cache<T>)?.get(key: key)
    }

    /// Set cached value
    /// - Parameters:
    ///   - key: Cache key
    ///   - value: Value to cache
    ///   - maxSize: Maximum cache size
    ///   - ttl: Time to live in seconds
    func setCachedValue<T>(key: String, value: T, maxSize: Int = 100, ttl: TimeInterval = 300) {
        if caches[key] == nil {
            caches[key] = Cache<T>(maxSize: maxSize, ttl: ttl)
        }
        (caches[key] as? Cache<T>)?.set(key: key, value: value)
    }

    /// Clear cached value
    /// - Parameter key: Cache key
    func clearCache(key: String) {
        caches.removeValue(forKey: key)
    }

    /// Clear all caches
    func clearAllCaches() {
        caches.removeAll()
    }

    // MARK: - Background Processing

    /// Execute task on background queue
    /// - Parameters:
    ///   - operation: Operation to execute
    ///   - completion: Completion handler called on main queue
    func executeInBackground<T>(
        _ operation: @escaping () throws -> T,
        completion: @escaping (Result<T, Error>) -> Void
    ) {
        backgroundQueue.async {
            do {
                let result = try operation()
                DispatchQueue.main.async {
                    completion(.success(result))
                }
            } catch {
                DispatchQueue.main.async {
                    completion(.failure(error))
                }
            }
        }
    }

    /// Execute async task on background queue
    /// - Parameter operation: Async operation to execute
    /// - Returns: Result of operation
    func executeInBackground<T>(_ operation: @escaping () async throws -> T) async throws -> T {
        return try await withCheckedThrowingContinuation { continuation in
            Task {
                do {
                    let result = try await operation()
                    continuation.resume(returning: result)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    // MARK: - Batch Operations

    /// Execute multiple operations concurrently
    /// - Parameter operations: Array of async operations
    /// - Returns: Array of results
    func executeConcurrently<T>(_ operations: [() async throws -> T]) async throws -> [T] {
        return try await withThrowingTaskGroup(of: T.self) { group in
            for operation in operations {
                group.addTask {
                    try await operation()
                }
            }

            var results: [T] = []
            for try await result in group {
                results.append(result)
            }
            return results
        }
    }

    /// Execute operations in batches to prevent overwhelming the system
    /// - Parameters:
    ///   - operations: Array of operations
    ///   - batchSize: Number of operations per batch
    ///   - delay: Delay between batches
    /// - Returns: Array of results
    func executeBatched<T>(
        _ operations: [() async throws -> T],
        batchSize: Int = 10,
        delay: TimeInterval = 0.1
    ) async throws -> [T] {
        var results: [T] = []

        for batch in operations.chunked(into: batchSize) {
            let batchResults = try await executeConcurrently(batch)
            results.append(contentsOf: batchResults)

            // Add delay between batches
            if delay > 0 {
                try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            }
        }

        return results
    }

    // MARK: - Performance Monitoring

    /// Start performance timer
    /// - Parameter operation: Operation name
    func startTimer(_ operation: String) {
        performanceTimers[operation] = Date()
        os_log("Starting operation: %@", log: perfLog, type: .info, operation)
    }

    /// End performance timer
    /// - Parameter operation: Operation name
    func endTimer(_ operation: String) {
        guard let startTime = performanceTimers[operation] else { return }
        let duration = Date().timeIntervalSince(startTime)
        performanceTimers.removeValue(forKey: operation)
        os_log("Completed operation: %@ in %.3f seconds", log: perfLog, type: .info, operation, duration)
    }

    /// Execute with performance monitoring
    /// - Parameters:
    ///   - operation: Operation name
    ///   - action: Action to execute
    /// - Returns: Result of action
    func executeWithTiming<T>(_ operation: String, action: () throws -> T) rethrows -> T {
        startTimer(operation)
        defer { endTimer(operation) }
        return try action()
    }

    /// Execute async with performance monitoring
    /// - Parameters:
    ///   - operation: Operation name
    ///   - action: Async action to execute
    /// - Returns: Result of action
    func executeWithTiming<T>(_ operation: String, action: () async throws -> T) async rethrows -> T {
        startTimer(operation)
        defer { endTimer(operation) }
        return try await action()
    }

    // MARK: - Memory Management

    /// Execute with memory pressure monitoring
    /// - Parameter action: Action to execute
    func executeWithMemoryMonitoring(_ action: () -> Void) {
        let memoryBefore = getMemoryUsage()
        action()
        let memoryAfter = getMemoryUsage()

        if memoryAfter > memoryBefore + 50 * 1024 * 1024 { // 50MB threshold
            os_log("High memory usage detected: %lld bytes", log: perfLog, type: .info, memoryAfter - memoryBefore)
        }
    }

    /// Get current memory usage
    /// - Returns: Memory usage in bytes
    private func getMemoryUsage() -> Int64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size) / 4

        let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: 1) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }

        if kerr == KERN_SUCCESS {
            return Int64(info.resident_size)
        } else {
            return 0
        }
    }

    // MARK: - Cleanup

    /// Clean up all resources
    func cleanup() {
        // Cancel all debouncers
        debouncers.values.forEach { $0.invalidate() }
        debouncers.removeAll()

        // Clear caches
        clearAllCaches()

        // Clear performance timers
        performanceTimers.removeAll()
    }

    deinit {
        cleanup()
    }
}

// MARK: - Array Extension for Batching

extension Array {
    /// Split array into chunks of specified size
    /// - Parameter size: Size of each chunk
    /// - Returns: Array of chunks
    func chunked(into size: Int) -> [[Element]] {
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0..<Swift.min($0 + size, count)])
        }
    }
}

// MARK: - Publisher Extension for Debouncing

extension Publisher {
    /// Debounce publisher with performance optimization
    /// - Parameters:
    ///   - delay: Delay in seconds
    ///   - scheduler: Scheduler to use
    /// - Returns: Debounced publisher
    func debounceOptimized<S: Scheduler>(
        for delay: S.SchedulerTimeType.Stride,
        scheduler: S
    ) -> Publishers.Debounce<Self, S> {
        return self.debounce(for: delay, scheduler: scheduler)
    }
}

// MARK: - Performance Metrics

/// Performance metrics for monitoring
struct PerformanceMetrics {
    let operation: String
    let duration: TimeInterval
    let memoryUsage: Int64
    let timestamp: Date

    init(operation: String, duration: TimeInterval, memoryUsage: Int64 = 0) {
        self.operation = operation
        self.duration = duration
        self.memoryUsage = memoryUsage
        self.timestamp = Date()
    }
}

// MARK: - Usage Examples

/*
 // Example usage of PerformanceOptimizer

 class ExampleViewController: UIViewController {

     override func viewDidLoad() {
         super.viewDidLoad()

         // Debounce search input
         PerformanceOptimizer.shared.debounceOnMain(
             key: "search",
             delay: 0.3
         ) {
             self.performSearch()
         }

         // Cache expensive calculations
         let result = PerformanceOptimizer.shared.getCachedValue(
             key: "calculations",
             type: [Double].self
         ) ?? performExpensiveCalculations()

         // Execute background task
         PerformanceOptimizer.shared.executeInBackground({
             return self.processLargeDataset()
         }) { result in
             switch result {
             case .success(let data):
                 self.updateUI(with: data)
             case .failure(let error):
                 self.handleError(error)
             }
         }
     }

     private func performSearch() {
         // Search implementation
     }

     private func performExpensiveCalculations() -> [Double] {
         // Expensive calculations
         let result = [1.0, 2.0, 3.0]
         PerformanceOptimizer.shared.setCachedValue(
             key: "calculations",
             value: result,
             ttl: 60 // Cache for 1 minute
         )
         return result
     }

     private func processLargeDataset() -> ProcessedData {
         // Large dataset processing
         return ProcessedData()
     }

     private func updateUI(with data: ProcessedData) {
         // UI update
     }

     private func handleError(_ error: Error) {
         // Error handling
     }
 }

 struct ProcessedData {
     // Data structure
 }
 */
