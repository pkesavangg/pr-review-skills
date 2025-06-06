import SwiftUI


// It is for testing purposes only, to ensure the FeedItemView works as expected.

struct FeedItemView: View {
    let feedItem: FeedItem
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Message type
            Text(feedItem.messageTypeText)
                .font(.caption)
                .foregroundColor(.secondary)
                .textCase(.uppercase)
            
            // Title with widow prevention
            Text(FeedTextFormatter.preventWidow(feedItem.titleText))
                .font(.headline)
                .fontOpenSans(.body2)
            
            // Subtitle with template formatting
            if let modalText = feedItem.subtitleModalText {
                Text(AttributedString(FeedTextFormatter.formatFeedTemplate(modalText, feedItem: feedItem)))
                    .fontOpenSans(.body2)
            }
            
            Text(AttributedString(FeedTextFormatter.formatFeedTemplate(feedItem.subtitleFeedText, feedItem: feedItem)))
                .font(.body)
            
            // Expiration date if available
            if let expiresAt = feedItem.expiresAt {
                Text(FeedTextFormatter.formatExpirationDate(expiresAt))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            // Link if available
            if let linkTarget = feedItem.linkTarget {
                Button(action: {
                    // Handle link action
                    if let url = URL(string: linkTarget) {
                        UIApplication.shared.open(url)
                    }
                }) {
                    Text(feedItem.linkText.uppercased())
                        .font(.body.bold())
                        .foregroundColor(.accentColor)
                }
            }
        }
        .padding()
    }
}

#if DEBUG
struct FeedItemView_Previews: PreviewProvider {
    static var previews: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Use static mock items
                ForEach(StaticFeedItems.items, id: \.feedPostId) { item in
                    FeedItemView(feedItem: item)
                        .background(Color(.systemBackground))
                        .cornerRadius(12)
                        .shadow(radius: 2)
                }
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
        .previewDisplayName("Feed Items")
    }
}
#endif


import Foundation

struct StaticFeedItems {
    static let items: [FeedItem] = [
        // Test Case 1: Testing all text formatting combinations
        FeedItem(
            feedPostId: "test_formatting_1",
            elementId: "elem_format_1",
            accountId: "acc_default",
            isUnread: true,
            messageTypeText: "TEXT FORMAT TEST",
            titleText: "Testing All Text Formats",
            // Expected output for each format:
            // - "Bold text" -> should be bold
            // - "Italic text" -> should be slanted
            // - "Strike text" -> should have line through
            // - "Bold-Italic" -> should be both bold and slanted
            // - "Strike-Bold-Italic" -> should have line through, be bold and slanted
            subtitleModalText: """
            Simple formats: {{bold[Bold text]}} {{italic[Italic text]}} {{strike[Strike text]}}
            Combined formats: {{bold-italic[Bold-Italic]}} {{strike-bold-italic[Strike-Bold-Italic]}}
            """,
            // Expected output:
            // - "$99.99" -> regular text
            // - "$149.99" -> struck through, bold, and italic
            subtitleFeedText: "Special price $99.99 {{strike-bold-italic[$149.99]}}",
            titleImage: "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
            linkTarget: "https://example.com",
            linkText: "TEST FORMATS",
            trigger: nil,
            expiresAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(7 * 24 * 3600)),
            feedType: .link,
            landingPage: nil
        ),

        // Test Case 2: Testing expiration date formatting
        FeedItem(
            feedPostId: "test_dates_1",
            elementId: "elem_dates_1",
            accountId: "acc_default",
            isUnread: true,
            messageTypeText: "DATE FORMAT TEST",
            titleText: "Testing Date Formats",
            // Expected output:
            // - First {{expiresAt}} -> should show "7 days" (or actual remaining time)
            // - Second {{expiresAt}} -> should show "1 day" (or actual remaining time)
            subtitleModalText: "Long expiry: {{expiresAt}}\nShort expiry: {{expiresAt}}",
            // Expected output: Should show actual remaining days
            subtitleFeedText: "This offer expires in {{expiresAt}}",
            titleImage: "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
            linkTarget: "https://example.com",
            linkText: "TEST DATES",
            trigger: nil,
            // Setting two different expiration dates for testing
            expiresAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(24 * 3600)), // 1 day
            feedType: .link,
            landingPage: nil
        ),

        // Test Case 3: Testing price formatting with multiple styles
        FeedItem(
            feedPostId: "test_prices_1",
            elementId: "elem_prices_1",
            accountId: "acc_default",
            isUnread: true,
            messageTypeText: "PRICE FORMAT TEST",
            titleText: "Testing Price Formats",
            // Expected output:
            // - "$25.99" -> bold only
            // - "$35.99" -> strike-through only
            // - "$45.99" -> bold and italic
            // - "$55.99" -> strike-through, bold, and italic
            subtitleModalText: """
            Current price {{bold[$25.99]}}
            Old price {{strike[$35.99]}}
            Sale price {{bold-italic[$45.99]}}
            MSRP {{strike-bold-italic[$55.99]}}
            """,
            subtitleFeedText: "Save big today!",
            titleImage: "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
            linkTarget: "https://example.com",
            linkText: "TEST PRICES",
            trigger: nil,
            expiresAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(3 * 24 * 3600)),
            feedType: .link,
            landingPage: nil
        ),
        
        // Kitchen Scales Deal
        FeedItem(
            feedPostId: "mockUUID0002",
            elementId: "elem_\(UUID().uuidString)",
            accountId: "acc_default",
            isUnread: true,
            messageTypeText: "LIGHTNING DEAL",
            titleText: "Kitchen Scales 40% Off",
            subtitleModalText: "Ends in {{expiresAt}} hurry {{bold[15$]}} 14$ {{strike-bold-italic[15$]}}",
            subtitleFeedText: "Test this price $10 <s>$20</s>",
            titleImage:
                "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            linkTarget: "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
            linkText: "BUY NOW",
            trigger: nil,
            expiresAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(7 * 24 * 3600)), // 7 days from now
            feedType: .landing,
            landingPage: LandingPage(
                feedLandingPageId: "landing_\(UUID().uuidString)",
                feedPostId: "mockUUID0002",
                titleText: "Kitchen Scales",
                promoCode: "SCALE40OFF",
                featuredImage: nil,
                supportingTitleText: "Premium Kitchen Scales",
                supportingDescriptionText: "Get {{bold[40% OFF]}} on our premium kitchen scales for precise measurements!",
                supportingImage: [
                    "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
                ],
                featuredTitleText: "Available Models",
                themeColor: .blue,
                featuredProduct: [
                    FeaturedProduct(
                        variationId: 1001,
                        titleText: "Digital Kitchen Scale",
                        feedLandingPageId: "landing_\(UUID().uuidString)",
                        linkText: "Shop Now",
                        linkTarget: "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
                        productImage: "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
                    )
                ]
            )
        ),
        
        // Verve Smart Scale Deal
        FeedItem(
            feedPostId: "testUUID20241",
            elementId: "elem_\(UUID().uuidString)",
            accountId: "acc_default",
            isUnread: true,
            messageTypeText: "Resolution Season Deal",
            titleText: "Get 10% off the Verve Smart Scale!",
            subtitleModalText: "Tap to get your 10% off code – good until the end of March!",
            subtitleFeedText: "Now - End of March",
            titleImage: "https://s3.amazonaws.com/gg-mark/wms/image/7txXZW0YxR6FoOqyVus35m.jpeg",
            linkTarget: "https://shop.greatergoods.com/pages/balance",
            linkText: "SHOP NOW",
            trigger: nil,
            expiresAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(30 * 24 * 3600)), // 30 days from now
            feedType: .landing,
            landingPage: LandingPage(
                feedLandingPageId: "landing_\(UUID().uuidString)",
                feedPostId: "testUUID20241",
                titleText: "Verve Smart Scale",
                promoCode: "VERVE10",
                featuredImage: nil,
                supportingTitleText: "Smart Tracking for Better Health",
                supportingDescriptionText: "Track your progress with our {{bold[Verve Smart Scale]}} - Now with {{bold[10% OFF]}}!",
                supportingImage: [
                    "https://s3.amazonaws.com/gg-mark/wms/image/7txXZW0YxR6FoOqyVus35m.jpeg"
                ],
                featuredTitleText: "Smart Features",
                themeColor: .red,
                featuredProduct: [
                    FeaturedProduct(
                        variationId: 1002,
                        titleText: "Verve Smart Scale",
                        feedLandingPageId: "landing_\(UUID().uuidString)",
                        linkText: "Get Yours",
                        linkTarget: "https://shop.greatergoods.com/pages/balance",
                        productImage: "https://s3.amazonaws.com/gg-mark/wms/image/7txXZW0YxR6FoOqyVus35m.jpeg"
                    )
                ]
            )
        ),
        
        // Coffee Grinder Deal
        FeedItem(
            feedPostId: "testUUID240312A",
            elementId: "elem_\(UUID().uuidString)",
            accountId: "acc_default",
            isUnread: true,
            messageTypeText: "LIMITED TIME DEAL",
            titleText: "Take 50% OFF the gG Burr Coffee Grinder",
            subtitleModalText: "Tap now & claim your discount code before it's gone!",
            subtitleFeedText: "Hurry! Offer Ends 3/31/24",
            titleImage: "https://s3.amazonaws.com/gg-mark/wms/image/2K6iuQ38NgnJ6H9U6wJFKQ.jpg",
            linkTarget: "https://shop.greatergoods.com/pages/50offcoffeegrinder",
            linkText: "SHOP NOW",
            trigger: nil,
            expiresAt: "2024-03-31T23:59:59.000Z",
            feedType: .landing,
            landingPage: LandingPage(
                feedLandingPageId: "landing_\(UUID().uuidString)",
                feedPostId: "testUUID240312A",
                titleText: "Burr Coffee Grinder",
                promoCode: "COFFEE50",
                featuredImage: nil,
                supportingTitleText: "Perfect Grind Every Time",
                supportingDescriptionText: "Experience the {{bold[perfect coffee grind]}} with our premium Burr Coffee Grinder - Now {{bold[50% OFF]}}!",
                supportingImage: [
                    "https://s3.amazonaws.com/gg-mark/wms/image/2K6iuQ38NgnJ6H9U6wJFKQ.jpg"
                ],
                featuredTitleText: "Premium Features",
                themeColor: .gray,
                featuredProduct: [
                    FeaturedProduct(
                        variationId: 1003,
                        titleText: "Burr Coffee Grinder",
                        feedLandingPageId: "landing_\(UUID().uuidString)",
                        linkText: "Get 50% Off",
                        linkTarget: "https://shop.greatergoods.com/pages/50offcoffeegrinder",
                        productImage: "https://s3.amazonaws.com/gg-mark/wms/image/2K6iuQ38NgnJ6H9U6wJFKQ.jpg"
                    )
                ]
            )
        ),
        
        // Baby Products Deal
        FeedItem(
            feedPostId: "testUUID240426A",
            elementId: "elem_\(UUID().uuidString)",
            accountId: "acc_default",
            isUnread: true,
            messageTypeText: "BABY BARGAINS",
            titleText: "Get 10% OFF gifts for moms-to-be!",
            subtitleModalText: "Tap now to claim your discount code before it's gone!",
            subtitleFeedText: "HURRY! Offer Ends 5/31/24",
            titleImage: "https://s3.amazonaws.com/gg-mark/wms/image/2Rh2e7DUPk16ZC3A4CzMj7.jpg",
            linkTarget: "https://shop.greatergoods.com/pages/10offbaby",
            linkText: "SHOP NOW",
            trigger: nil,
            expiresAt: "2024-05-31T23:59:59.000Z",
            feedType: .landing,
            landingPage: LandingPage(
                feedLandingPageId: "landing_\(UUID().uuidString)",
                feedPostId: "testUUID240426A",
                titleText: "Baby Care Essentials",
                promoCode: "BABY10",
                featuredImage: nil,
                supportingTitleText: "For the New Arrivals",
                supportingDescriptionText: "Get {{bold[10% OFF]}} on our complete range of {{bold[baby care products]}} - Perfect gifts for expecting mothers!",
                supportingImage: [
                    "https://s3.amazonaws.com/gg-mark/wms/image/2Rh2e7DUPk16ZC3A4CzMj7.jpg"
                ],
                featuredTitleText: "Essential Products",
                themeColor: .red,
                featuredProduct: [
                    FeaturedProduct(
                        variationId: 1004,
                        titleText: "Baby Care Bundle",
                        feedLandingPageId: "landing_\(UUID().uuidString)",
                        linkText: "Shop Collection",
                        linkTarget: "https://shop.greatergoods.com/pages/10offbaby",
                        productImage: "https://s3.amazonaws.com/gg-mark/wms/image/2Rh2e7DUPk16ZC3A4CzMj7.jpg"
                    )
                ]
            )
        ),
        FeedItem(
            feedPostId: "SV7OXT1ywEGGjbHOqLflRq",
            elementId: "elem_1",
            accountId: "acc_1",
            isUnread: true,
            messageTypeText: "LIGHTNING DEAL",
            titleText: "Kitchen Scales 40% Off",
            subtitleModalText: "Ends in {{expiresAt}} hurry {{bold[15$]}} 14$ {{strike-bold-italic[15$]}}",
            subtitleFeedText: "Be prepare for the holidays! Offer ends in {{expiresAt}}!",
            titleImage: "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg",
            linkTarget: "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
            linkText: "BUY NOW",
            trigger: nil,
            expiresAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(24 * 3600)), // 24 hours from now
            feedType: .landing,
            landingPage: LandingPage(
                feedLandingPageId: "veAHqpkemoBrnyyRQKoAFH",
                feedPostId: "SV7OXT1ywEGGjbHOqLflRq",
                titleText: "Vacuum Sealers",
                promoCode: "5ZHTL9M8",
                featuredImage: nil,
                supportingTitleText: "One Machine, a Million Uses",
                supportingDescriptionText: "The Greater Goods {{bold[All-in-One Vacuum Sealer]}} has built-in bag storage and a slicer for hassle-free meal prep!",
                supportingImage: [
                    "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP_1.jpg",
                    "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP_2.jpg"
                ],
                featuredTitleText: "Three Colors",
                themeColor: .green,
                featuredProduct: [
                    FeaturedProduct(
                        variationId: 10001,
                        titleText: "Stone Blue",
                        feedLandingPageId: "veAHqpkemoBrnyyRQKoAFH",
                        linkText: "Shop",
                        linkTarget: "https://shop.greatergoods.com/collections/food-scales/products/greatergoods-digital-food-kitchen-scale",
                        productImage: "https://s3.amazonaws.com/gg-mark/wms/image/6rWSd7o0agFUzr3ZIqiXJP.jpg"
                    )
                ]
            )
        ),
        // Add more static items as needed
        FeedItem(
            feedPostId: "2",
            elementId: "elem_2",
            accountId: "acc_1",
            isUnread: true,
            messageTypeText: "SPECIAL OFFER",
            titleText: "Holiday Special: Smart Scale Bundle",
            subtitleModalText: "Get {{bold[25% OFF]}} when you buy any two scales!",
            subtitleFeedText: "Limited time offer - ends in {{expiresAt}}",
            titleImage: "placeholder_bundle",
            linkTarget: "https://shop.greatergoods.com/collections/bundles",
            linkText: "SHOP BUNDLE",
            trigger: nil,
            expiresAt: "2025-12-30T06:00:00.000Z", // 7 days from now
            feedType: .link,
            landingPage: nil
        )
    ]
}
