import Foundation

enum UnexpectedCallError: Error, Equatable {
    case methodCalled(String)
}
