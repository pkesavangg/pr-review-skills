//
//  Router.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 28/05/25.
//



import Foundation
import SwiftUI

// MARK: - Router
//  An observable navigation manager that conforms to `RoutableObject` for handling navigation stack logic.
//  - Holds a published stack of `Routes` to drive navigation updates in SwiftUI.
//  - Designed for use with `RoutingView` to enable programmatic navigation.
//

public final class Router<Routes: Routable>: RoutableObject, ObservableObject {
    public typealias Destination = Routes

    @Published public var stack: [Routes] = []

    public init() {}
}
