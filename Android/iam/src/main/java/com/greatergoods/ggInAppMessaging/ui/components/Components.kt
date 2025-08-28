package com.greatergoods.ggInAppMessaging.ui.components

/**
 * Components index file
 * Exports all UI components for easy importing
 */

/**
 * Component usage examples:
 *
 * // Feed Item Display
 * FeedItemComponent(
 *     feedItem = feedItem,
 *     onItemClick = { /* handle click */ },
 *     onShopNowClick = { /* handle shop now */ }
 * )
 *
 * // Feed Modal
 * FeedModalComponent(
 *     feedItem = feedItem,
 *     onDismiss = { /* close modal */ },
 *     onShopNowClick = { /* handle shop now */ },
 *     onCopyPromoCode = { /* copy promo code */ }
 * )
 *
 * // Feed Landing Page
 * FeedLandingPageComponent(
 *     feedItem = feedItem,
 *     onDismiss = { /* close page */ },
 *     onNavigateToFAQ = { /* open FAQ */ },
 *     onProductClick = { url, variationId -> /* handle product click */ },
 *     onPromoCodeCopy = { /* copy promo code */ }
 * )
 *
 * // Feed Settings
 * FeedSettingsComponent(
 *     feedSetting = currentSettings,
 *     onDismiss = { /* close settings */ },
 *     onSettingsChanged = { newSettings -> /* save settings */ }
 * )
 *
 * // FAQ
 * FAQComponent(
 *     onDismiss = { /* close FAQ */ }
 * )
 *
 * // Lazy Image
 * LazyImageComponent(
 *     imageUrl = "https://example.com/image.jpg",
 *     placeholderUrl = "https://example.com/placeholder.jpg",
 *     onImageLoaded = { /* image loaded */ },
 *     onImageError = { /* image error */ }
 * )
 */
