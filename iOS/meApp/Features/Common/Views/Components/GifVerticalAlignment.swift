///
///  GifVerticalAlignment.swift
///  meApp
///

enum GifVerticalAlignment {
    case top
    case center
    case bottom

    var objectPosition: String {
        switch self {
        case .top: return "center top"
        case .center: return "center center"
        case .bottom: return "center bottom"
        }
    }
}
