/// Constants for feed-related time intervals in milliseconds
enum FeedTimestamp {
    static let oneDay = 24 * 60 * 60 * 1000
    static let twoDays = 48 * 60 * 60 * 1000
    static let oneHour = 60 * 60 * 1000
    static let oneMinute = 60 * 1000
    static let oneWeek = 7 * 24 * 60 * 60 * 1000
}

/// Constants for time unit display strings
enum UnitsOfTime {
    static let minute = "a minute"
    static let minutes = "minutes"
    static let hours = "hours"
    static let days = "days"
}

/// External link URLs
enum ExternalLinks {
    static let ggShopify = "https://shop.greatergoods.com"
}

/// Text formatting and display constants
enum FeedTextConstants {
    static let feedModalTitleWordCount = 3
    static let nonBreakingSpace = "\u{00A0}"
    static let contentWithinSquareBracketsRegex = "\\[(.*?)\\]"
    static let contentExcludingSquareBracketsRegex = "\\s*(?:\\[[^\\]]*\\])\\s*"
    static let contentWithinCurlyBracketsRegex = "\\{{(.*?)}}"
} 
