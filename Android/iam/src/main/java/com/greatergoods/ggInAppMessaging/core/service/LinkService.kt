package com.greatergoods.ggInAppMessaging.core.service

import android.content.Context
import com.greatergoods.ggInAppMessaging.domain.services.ILinkService
import com.greatergoods.ggInAppMessaging.util.LinkOpener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service implementation for handling link operations
 * Uses LinkOpener utility for opening links in custom tabs or browsers
 */
@Singleton
class LinkService @Inject constructor(
    @ApplicationContext private val context: Context
) : ILinkService {

    override suspend fun openInCustomTab(url: String, showTitle: Boolean) {
        try {
            if (url.isNotEmpty()) {
                LinkOpener.openInCustomTab(
                    context = context,
                    url = url,
                    showTitle = showTitle
                )
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun openInChromeOrDefault(url: String) {
        try {
            if (url.isNotEmpty()) {
                LinkOpener.openInChromeOrDefault(context, url)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun isValidUrl(url: String): Boolean {
        return try {
            url.isNotEmpty() &&
            (url.startsWith("http://") || url.startsWith("https://"))
        } catch (e: Exception) {
            false
        }
    }
}
