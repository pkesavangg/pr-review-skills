public enum WifiErrorCode: String, CaseIterable, Identifiable, Hashable {
    case t163
    case t164
    case t165
    case t204
    case t205
    case t206
    case t315
    case t323
    case t325

    public var id: String { rawValue }
    
    /// Indicates whether this error code should display its messages in numbered format.
    public var shouldUseNumberedMessages: Bool {
        switch self {
        case .t206, .t163, .t323:
            return true
        default:
            return false
        }
    }
}
