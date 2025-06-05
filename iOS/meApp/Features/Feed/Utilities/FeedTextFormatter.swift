import Foundation
import UIKit

struct FeedTextFormatter {
    
    // MARK: - Expiration Date Formatting
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
    static func truncate(_ text: String, limit: Int = 20) -> String {
        guard text.count > limit else { return text }
        let index = text.index(text.startIndex, offsetBy: limit)
        return text[..<index] + "..."
    }
    
    // MARK: - Helper Methods
    private static func formatTypeAttributes(_ formatType: String) -> [NSAttributedString.Key: Any] {
        var attributes: [NSAttributedString.Key: Any] = [:]
        
        let types = formatType.components(separatedBy: "-")
        for type in types {
            switch FeedTextFormatType(rawValue: type) {
            case .bold:
                attributes[.font] = UIFont.boldSystemFont(ofSize: UIFont.systemFontSize)
            case .strike:
                attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
            case .italic:
                attributes[.font] = UIFont.italicSystemFont(ofSize: UIFont.systemFontSize)
            case .underline:
                attributes[.underlineStyle] = NSUnderlineStyle.single.rawValue
            case .none:
                break
            }
        }
        
        return attributes
    }
    
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

// MARK: - Feed Item Extension
extension FeedItem {
    func getValue(for key: String) -> Date? {
        let mirror = Mirror(reflecting: self)
        for child in mirror.children {
            if child.label == key, let date = child.value as? Date {
                return date
            }
        }
        return nil
    }
} 
