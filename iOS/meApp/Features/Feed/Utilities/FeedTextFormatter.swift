import Foundation
import UIKit

struct FeedTextFormatter {
    
    // MARK: - Expiration Date Formatting
    /// Formats an ISO8601 date string into a human-readable expiration message
    /// - If more than 7 days: Shows "Offer valid through MM/dd/yyyy"
    /// - If less than 7 days: Shows "Offer expires in X days"
    /// - If invalid or expired: Returns empty string
    /// - Parameter expiresAtString: ISO8601 formatted date string
    /// - Returns: Formatted expiration message
    static func formatExpirationDate(_ expiresAtString: String?) -> String {
        guard let expiresAtString = expiresAtString,
              let expiresAt = ISO8601DateFormatter().date(from: expiresAtString) else {
            return ""
        }
        
        let currentDate = Date()
        let timeDiff = expiresAt.timeIntervalSince(currentDate)
        let daysLeft = Int(timeDiff / (24 * 3600))
        
        if daysLeft > 7 {
            let formatter = DateFormatter()
            formatter.dateFormat = "MM/dd/yyyy"
            return "Offer valid through \(formatter.string(from: expiresAt))"
        } else if daysLeft >= 0 {
            return "Offer expires in \(daysLeft) day\(daysLeft == 1 ? "" : "s")"
        }
        return ""
    }
    
    // MARK: - Feed Template Formatting
    /// Processes a template string containing variables in {{variable}} format
    /// Supports formatting types like {{bold[text]}}, {{italic[text]}}, etc.
    /// Also handles special variables like {{expiresAt}}
    /// - Parameters:
    ///   - inputString: Template string with variables
    ///   - feedItem: FeedItem containing data to populate variables
    /// - Returns: Formatted NSAttributedString with applied styles
    static func formatFeedTemplate(_ inputString: String, feedItem: FeedItem) -> NSAttributedString {
        let attributedString = NSMutableAttributedString(string: inputString)
        
        // Regular expression for matching {{content}}
        let pattern = "\\{\\{([^}]+)\\}\\}"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return attributedString }
        
        let matches = regex.matches(in: inputString, range: NSRange(inputString.startIndex..., in: inputString))
        
        // Process matches in reverse to not affect other ranges
        for match in matches.reversed() {
            let range = match.range(at: 1)
            guard let swiftRange = Range(range, in: inputString) else { continue }
            
            let variable = String(inputString[swiftRange])
            
            // Handle different formatting types
            if variable.contains("[") {
                let components = variable.components(separatedBy: "[")
                if components.count == 2 {
                    let formatType = components[0]
                    let content = components[1].replacingOccurrences(of: "]", with: "")
                    
                    let attributes = formatTypeAttributes(formatType)
                    let formattedString = NSAttributedString(string: content, attributes: attributes)
                    
                    let fullRange = match.range(at: 0)
                    attributedString.replaceCharacters(in: fullRange, with: formattedString)
                }
            } else if variable == "expiresAt", let expiresAt = feedItem.expiresAt {
                // Handle expiration date
                let formattedValue = formatDateToText(expiresAt)
                let fullRange = match.range(at: 0)
                attributedString.replaceCharacters(in: fullRange, with: formattedValue)
            }
        }
        
        return attributedString
    }
    
    // MARK: - No Widow Prevention
    /// Prevents single words from appearing alone on the last line (widows)
    /// by joining the last two words with a non-breaking space
    /// Only applies if the last two words are less than half the total text length
    /// - Parameter text: Input text to process
    /// - Returns: Text with widow prevention applied
    static func preventWidow(_ text: String) -> String {
        let words = text.components(separatedBy: " ")
        guard words.count > 3 else { return text }
        
        var result = words
        let lastTwoWords = words.suffix(2).joined(separator: " ")
        if Double(lastTwoWords.count) < Double(text.count) / 2 {
            result[result.count - 2] = result[result.count - 2] + "\u{00A0}" + result.last!
            result.removeLast()
        }
        return result.joined(separator: " ")
    }
    
    // MARK: - Truncate Text
    /// Truncates text to specified length and adds ellipsis
    /// - Parameters:
    ///   - text: Text to truncate
    ///   - limit: Maximum length (default: 20)
    /// - Returns: Truncated text with ellipsis if needed
    static func truncate(_ text: String, limit: Int = 20) -> String {
        guard text.count > limit else { return text }
        let index = text.index(text.startIndex, offsetBy: limit)
        return text[..<index] + "..."
    }
    
    // MARK: - Helper Methods
    /// Formats text attributes based on the specified format type
    /// - Parameter formatType: String representing the format type (e.g., "bold", "italic", "underline")
    /// - Returns: Dictionary of attributes for the specified format type
    private static func formatTypeAttributes(_ formatType: String) -> [NSAttributedString.Key: Any] {
        var attributes: [NSAttributedString.Key: Any] = [:]
        var traits: UIFontDescriptor.SymbolicTraits = []
        let fontSize: CGFloat = 16.0
        
        let types = formatType.components(separatedBy: "-")
        for type in types {
            switch FeedTextFormatType(rawValue: type) {
            case .bold:
                traits.insert(.traitBold)
            case .strike:
                attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
            case .italic:
                traits.insert(.traitItalic)
            case .underline:
                attributes[.underlineStyle] = NSUnderlineStyle.single.rawValue
            case .none:
                break
            }
        }
        
        // Determine the appropriate OpenSans font name based on traits
        var fontName = "OpenSans-Regular"
        if traits.contains(.traitBold) {
            fontName = traits.contains(.traitItalic) ? "OpenSans-BoldItalic" : "OpenSans-Bold"
        } else if traits.contains(.traitItalic) {
            fontName = "OpenSans-Italic"
        }
        
        // Try to create font with the determined name
        if let font = UIFont(name: fontName, size: fontSize) {
            attributes[.font] = font
        } else {
            // Fallback to system font if OpenSans is not available
            if let descriptor = UIFont.systemFont(ofSize: fontSize).fontDescriptor.withSymbolicTraits(traits) {
                attributes[.font] = UIFont(descriptor: descriptor, size: fontSize)
            }
        }
        
        return attributes
    }
    
    /// Formats a date string into a human-readable time remaining message
    /// - Parameter dateString: ISO8601 formatted date string
    /// - Returns: Formatted time remaining message
    private static func formatDateToText(_ dateString: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        guard let date = formatter.date(from: dateString) else {
            // Try without fractional seconds if first attempt fails
            formatter.formatOptions = [.withInternetDateTime]
            guard let date = formatter.date(from: dateString) else {
                return ""
            }
            return formatTimeRemaining(from: date)
        }
        return formatTimeRemaining(from: date)
    }
    
    /// Formats a date into a human-readable time remaining message
    /// - Parameter date: Date to format
    /// - Returns: Formatted time remaining message
    private static func formatTimeRemaining(from date: Date) -> String {
        let now = Date()
        let diff = date.timeIntervalSince(now)
        
        if diff > 0 {
            if diff <= 60 {
                return "1 minute"
            } else if diff < 3600 {
                let minutes = Int(diff/60)
                return "\(minutes) minute\(minutes == 1 ? "" : "s")"
            } else if diff < 86400 { // 1 day
                let hours = Int(diff/3600)
                return "\(hours) hour\(hours == 1 ? "" : "s")"
            } else {
                let days = Int(diff/86400)
                return "\(days) day\(days == 1 ? "" : "s")"
            }
        }
        return "Expired"
    }
} 
