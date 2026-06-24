import SwiftUI

/// Constants for promotion code themes and their associated colors
enum PromoCodeTheme {
    case red
    case green
    case blue
    case gray
    
    /// Color for the promo code text
    var promoCodeColor: Color {
        switch self {
        case .red:
            return Color(hex: "#D9675C")
        case .green:
            return Color(hex: "#9DAD99")
        case .blue:
            return Color(hex: "#4E738A")
        case .gray:
            return Color(hex: "#424242")
        }
    }
    
    /// Background color for the promo code in light mode
    var promoCodeBgColor: Color {
        switch self {
        case .red:
            return Color(hex: "#B8584E", alpha: 0.2)
        case .green:
            return Color(hex: "#6E796B", alpha: 0.2)
        case .blue:
            return Color(hex: "#4E738A", alpha: 0.2)
        case .gray:
            return Color(hex: "#FCF8F4")
        }
    }
    
    /// Background color for the copy button in light mode
    var copyButtonBgColor: Color {
        switch self {
        case .red:
            return Color(hex: "#B8584E")
        case .green:
            return Color(hex: "#6E796B")
        case .blue:
            return Color(hex: "#4E738A")
        case .gray:
            return Color(hex: "#424242")
        }
    }
    
    /// Background color for the promo code in dark mode
    var promoCodeBgColorDarkMode: Color {
        switch self {
        case .red:
            return Color(hex: "#D9675C", alpha: 0.2)
        case .green:
            return Color(hex: "#9DAD99", alpha: 0.2)
        case .blue:
            return Color(hex: "#839DAD", alpha: 0.2)
        case .gray:
            return Color(hex: "#FCF8F4", alpha: 0.2)
        }
    }
    
    /// Background color for the copy button in dark mode
    var copyButtonBgColorDarkMode: Color {
        switch self {
        case .red:
            return Color(hex: "#D9675C")
        case .green:
            return Color(hex: "#9DAD99")
        case .blue:
            return Color(hex: "#839DAD")
        case .gray:
            return Color(hex: "#FCF8F4")
        }
    }
}

private extension Color {
    /// Initialize a Color from a hex string with optional alpha
    init(hex: String, alpha: Double = 1.0) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")
        
        var rgb: UInt64 = 0
        Scanner(string: hexSanitized).scanHexInt64(&rgb)
        
        let red = Double((rgb & 0xFF0000) >> 16) / 255.0
        let green = Double((rgb & 0x00FF00) >> 8) / 255.0
        let blue = Double(rgb & 0x0000FF) / 255.0
        
        self.init(red: red, green: green, blue: blue, opacity: alpha)
    }
} 
