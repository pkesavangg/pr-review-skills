package com.greatergoods.ggInAppMessaging.core.utilities

import com.greatergoods.ggInAppMessaging.domain.constants.FeedStrings
import com.greatergoods.ggInAppMessaging.domain.models.UnitsOfTime
import java.text.SimpleDateFormat
import java.util.*

/**
 * Feed utility class for data processing
 * Android equivalent of Angular pipes
 */
object FeedUtility {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    /**
     * Format expiration date for display
     * Android equivalent of expiration-date.pipe.ts
     */
    fun formatExpirationDate(expiresAt: String?): String {
        if (expiresAt.isNullOrEmpty()) return ""

        return try {
            val date = dateFormat.parse(expiresAt)
            date?.let { displayDateFormat.format(it) } ?: expiresAt
        } catch (e: Exception) {
            expiresAt
        }
    }

    /**
     * Get time until expiration in human-readable format
     * Android equivalent of nowidow.pipe.ts
     */
    fun getTimeUntilExpiration(expiresAt: String?): String {
        if (expiresAt.isNullOrEmpty()) return ""

        return try {
            val expirationDate = dateFormat.parse(expiresAt)
            val now = Date()

            if (expirationDate == null) return expiresAt

            val timeDiff = expirationDate.time - now.time

            when {
                timeDiff < 0 -> "Expired"
                timeDiff < 60 * 1000 -> "Expires in less than a minute"
                timeDiff < 60 * 60 * 1000 -> {
                    val minutes = (timeDiff / (60 * 1000)).toInt()
                    "Expires in $minutes ${if (minutes == 1) UnitsOfTime.MINUTE else UnitsOfTime.MINUTES}"
                }
                timeDiff < 24 * 60 * 60 * 1000 -> {
                    val hours = (timeDiff / (60 * 60 * 1000)).toInt()
                    "Expires in $hours ${UnitsOfTime.HOURS}"
                }
                else -> {
                    val days = (timeDiff / (24 * 60 * 60 * 1000)).toInt()
                    "Expires in $days ${UnitsOfTime.DAYS}"
                }
            }
        } catch (e: Exception) {
            expiresAt
        }
    }

    /**
     * Truncate text to specified length
     * Android equivalent of truncate.pipe.ts
     */
    fun truncateText(text: String?, maxLength: Int = 100): String {
        if (text.isNullOrEmpty()) return ""
        if (text.length <= maxLength) return text

        return text.substring(0, maxLength - 3) + "..."
    }

    /**
     * Format feed template with variables
     * Android equivalent of feed-template.pipe.ts
     */
    fun formatFeedTemplate(template: String, variables: Map<String, String>): String {
        var result = template

        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }

        return result
    }

    /**
     * Check if feed is expired
     */
    fun isFeedExpired(expiresAt: String?): Boolean {
        if (expiresAt.isNullOrEmpty()) return false

        return try {
            val expirationDate = dateFormat.parse(expiresAt)
            val now = Date()

            expirationDate?.before(now) ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get feed priority based on expiration and unread status
     */
    fun getFeedPriority(feedItem: com.greatergoods.ggInAppMessaging.domain.models.FeedItem): Int {
        var priority = 0

        // Unread feeds get higher priority
        if (feedItem.isUnread) priority += 10

        // Feeds with triggers get higher priority
        if (!feedItem.trigger.isNullOrEmpty()) priority += 5

        // Feeds close to expiration get higher priority
        if (!feedItem.expiresAt.isNullOrEmpty()) {
            try {
                val expirationDate = dateFormat.parse(feedItem.expiresAt)
                val now = Date()

                if (expirationDate != null) {
                    val timeDiff = expirationDate.time - now.time
                    val hoursUntilExpiration = timeDiff / (60 * 60 * 1000)

                    when {
                        hoursUntilExpiration < 1 -> priority += 8
                        hoursUntilExpiration < 24 -> priority += 6
                        hoursUntilExpiration < 72 -> priority += 4
                        hoursUntilExpiration < 168 -> priority += 2 // 1 week
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }

        return priority
    }

    /**
     * Format promo code for display
     */
    fun formatPromoCode(promoCode: String?): String {
        if (promoCode.isNullOrEmpty()) return ""

        // Add spacing for better readability (e.g., "SAVE20" -> "SAVE 20")
        return promoCode.replace(Regex("([A-Z])([0-9])"), "$1 $2")
    }

    /**
     * Get theme color from string
     */
    fun getThemeColor(colorString: String?): androidx.compose.ui.graphics.Color {
        return when (colorString?.lowercase()) {
            "green" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
            "red" -> androidx.compose.ui.graphics.Color(0xFFF44336)
            "blue" -> androidx.compose.ui.graphics.Color(0xFF2196F3)
            "gray" -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
            else -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Default blue
        }
    }
}
