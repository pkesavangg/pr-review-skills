package com.greatergoods.ggInAppMessaging.domain.services

/**
 * Service interface for handling link operations
 * Handles opening external links, custom tabs, and other link-related functionality
 */
interface ILinkService {
    /**
     * Opens a link in a custom tab
     * @param url The URL to open
     * @param showTitle Whether to show the page title in the custom tab
     */
    suspend fun openInCustomTab(url: String, showTitle: Boolean = true)

    /**
     * Opens a link in Chrome or default browser
     * @param url The URL to open
     */
    suspend fun openInChromeOrDefault(url: String)

    /**
     * Validates if a URL is valid and safe to open
     * @param url The URL to validate
     * @return true if the URL is valid and safe
     */
    suspend fun isValidUrl(url: String): Boolean
}
