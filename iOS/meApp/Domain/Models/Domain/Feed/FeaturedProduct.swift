/// Represents a featured product in a feed landing page.
struct FeaturedProduct: Codable, Equatable {
    /// The variation ID of the product.
    let variationId: Int
    /// The title text for the product.
    let titleText: String
    /// The ID of the associated feed landing page.
    let feedLandingPageId: String
    /// The text for the product link.
    let linkText: String
    /// The target URL or route for the product link.
    let linkTarget: String
    /// The image URL or asset name for the product.
    let productImage: String
} 