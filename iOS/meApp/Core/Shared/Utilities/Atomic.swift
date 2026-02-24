//
//  Atomic.swift
//  meApp
//
//  Created by Cursor AI on 27/06/25.
//
//  A lightweight, thread-safe property wrapper that provides atomic read/write
//  access to the wrapped value using an `NSLock`. This is more than sufficient
//  for the simple Boolean flag (`skipCheckNetwork`) that needs cross-thread
//  protection in `HTTPClient`, while keeping the public API unchanged.
//
//  Usage:
//  ```swift
//  @Atomic public var isFlagEnabled: Bool = false
//  ```
//  Reads and writes to `isFlagEnabled` are now guaranteed to be serialised.
//
import Foundation

/// A simple, generic atomic property wrapper.
@propertyWrapper
public struct Atomic<Value> {
    private var _value: Value
    private let lock = NSLock()

    public init(wrappedValue: Value) {
        self._value = wrappedValue
    }

    public var wrappedValue: Value {
        get {
            lock.lock(); defer { lock.unlock() }
            return _value
        }
        set {
            lock.lock(); _value = newValue; lock.unlock()
        }
    }

    /// Provides in-place, thread-safe mutation of the underlying value.
    /// - Parameter transform: Closure that receives an `inout` reference to the
    ///   wrapped value.
    /// - Returns: The value returned by `transform`.
    @discardableResult
    public mutating func withValue<R>(_ transform: (inout Value) throws -> R) rethrows -> R {
        lock.lock(); defer { lock.unlock() }
        return try transform(&_value)
    }
}
