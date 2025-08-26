package com.greatergoods.ggInAppMessaging.domain.constants

/**
 * Feed application strings and constants
 * Android equivalent of Angular feed-strings.ts
 */
object FeedStrings {

    object Feeds {
        const val TITLE = "Messages"
        const val SUB_TITLE = "Deals on Goods"
        const val NO_ENTRY_TITLE = "Dry on Deals...for Now"
        const val NO_ENTRY_TEXT = "check back soon"
        const val NO_NETWORK_TITLE = "Looks like you're offline..."
        const val NO_NETWORK_TEXT = "Check back when connected"
        const val MESSAGE_SETTING = "Message Settings"
        const val POPUP_MESSAGE = "Pop-up Messages"
        const val NOTIFICATION = "Notification Badges"
        const val MESSAGE_SETTINGS = "Message Settings"

        object FeedLandingPage {
            const val COPY = "Copy"
            const val SHOP = "Shop"
            const val COPY_RIGHTS = "© Copyright 2024 Greater Goods"

            object Promotion {
                const val MESSAGE_PREFIX = "Use "
                const val CODE = "PROMO CODE"
                const val MESSAGE_SUFFIX = " at checkout!"
            }
        }

        object FaqList {
            object Faq1 {
                const val QUERY = "How do I redeem the code on Amazon?"
                const val RESPONSE = "On the checkout page, look for an option to enter a gift card or promotional code under 'Payment Method'. Enter your coupon code and click 'Apply'."
                const val IMAGE_SOURCE = "amazon_faq.png"
                const val ID = "faq1"
            }

            object Faq2 {
                const val QUERY = "I have another question"
                const val RESPONSE = "Send an email to hello@greatergoods.com. Our customer service team is happy to help."
                const val ID = "faq2"
            }
        }
    }

    object Assets {
        const val IMAGE_BASE_URL = "https://assets.greatergoods.com/images/"

        object Image {
            const val AMAZON_FAQ = "${IMAGE_BASE_URL}amazon-faq.png"
        }
    }
}
