//
//  RoutableObject.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//

import SwiftUI

// MARK: - RoutableObject
//  Defines the `Routable` typealias and `RoutableObject` protocol for managing a navigation stack.
//
//  - `Routable`: A typealias for any SwiftUI `View` that is also `Hashable`, used as a navigation destination.
//  - `RoutableObject`: A protocol for objects that manage a navigation stack of `Routable` destinations.
//  - Provides default implementations for common navigation operations like push, pop, reset, and replace.
//
//  This is intended to be used with a `Router` class and `RoutingView` for building a modular navigation system.
//

public typealias Routable = View & Hashable

public protocol RoutableObject: AnyObject {

    associatedtype Destination: Routable

    var stack: [Destination] { get set }

    func navigateBack(_ count: Int)

    func navigateBack(to destination: Destination)

    func navigateToRoot()

    func navigate(to destination: Destination)

    func navigate(to destinations: [Destination])

    func replace(with destinations: [Destination])
}

extension RoutableObject {
    public func navigateBack(_ count: Int = 1) {
        guard count > 0 else { return }
        guard count <= stack.count else {
            stack = .init()
            return
        }
        stack.removeLast(count)
    }

    public func navigateBack(to destination: Destination) {
        // Check if the destination exists in the stack
        if let index = stack.lastIndex(where: { $0 == destination }) {
            // Remove destinations above the specified destination
            stack.truncate(to: index)
        }
    }

    public func navigateToRoot() {
        stack = []
    }

    public func navigate(to destination: Destination) {
        stack.append(destination)
    }

    public func navigate(to destinations: [Destination]) {
        stack += destinations
    }

    public func replace(with destinations: [Destination]) {
        stack = destinations
    }
}
