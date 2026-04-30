package com.greatergoods.ggInAppMessaging.domain.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Settings controlling feed pop-ups & badge visibility.
 * Android equivalent of iOS FeedSetting
 */
data class FeedSetting(
    /** Whether a popup modal should be shown on login trigger. */
    @SerializedName("showPopupMessage")
    val showPopupMessage: Boolean,

    /** Whether the notification badge should be displayed. */
    @SerializedName("showNotificationBadge")
    val showNotificationBadge: Boolean
) {
    companion object {
        private val gson = Gson()

        /**
         * Convert FeedSetting to JSON string
         */
        fun FeedSetting.toJson(): String = gson.toJson(this)

        /**
         * Parse JSON string to FeedSetting
         */
        fun fromJson(json: String): FeedSetting = gson.fromJson(json, FeedSetting::class.java)
    }
}
