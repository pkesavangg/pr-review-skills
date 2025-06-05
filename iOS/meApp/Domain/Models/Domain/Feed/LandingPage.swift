/// Represents a landing page for a feed item.
struct LandingPage: Codable, Equatable {
    /// The unique identifier for the landing page.
    let feedLandingPageId: String
    /// The associated feed post ID.
    let feedPostId: String
    /// The main title text.
    let titleText: String
    /// Optional: The promo code for the landing page.
    let promoCode: String?
    /// Optional: The featured image URL or asset name.
    let featuredImage: String?
    /// Optional: The supporting title text.
    let supportingTitleText: String?
    /// Optional: The supporting description text.
    let supportingDescriptionText: String?
    /// Optional: Array of supporting image URLs or asset names.
    let supportingImage: [String]?
    /// Optional: The featured section title text.
    let featuredTitleText: String?
    /// Optional: The theme color for the landing page.
    let themeColor: ThemeColor?
    /// Optional: Array of featured products.
    let featuredProduct: [FeaturedProduct]?
} 