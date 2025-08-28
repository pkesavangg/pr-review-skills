package com.greatergoods.ggInAppMessaging.ui.screens

/**
 * IAM Package Screen Components
 *
 * This file serves as an index for all screen components.
 *
 * Available Screens:
 * - FeedMessagesScreen: Main screen content showing deals and messages (no navigation header)
 * - FeedMessagesSettingsScreen: Settings screen content for message preferences (no navigation header)
 *
 * Note: These are content-only composables. The app provides the top navigation bar.
 *
 * Usage:
 * import com.greatergoods.ggInAppMessaging.ui.screens.FeedMessagesScreen
 * import com.greatergoods.ggInAppMessaging.ui.screens.FeedMessagesSettingsScreen
 */

/**
 * Example usage in your main app:
 *
 * // Navigate to Feed Messages Screen Content
 * FeedMessagesScreen(
 *     onSettingsPress = { /* Navigate to settings */ }
 * )
 *
 * // Navigate to Feed Messages Settings Screen Content
 * FeedMessagesSettingsScreen(
 *     onPopUpMessagesToggle = { enabled -> /* Handle toggle */ },
 *     onNotificationBadgesToggle = { enabled -> /* Handle toggle */ },
 *     popUpMessagesEnabled = true,
 *     notificationBadgesEnabled = true
 * )
 */
