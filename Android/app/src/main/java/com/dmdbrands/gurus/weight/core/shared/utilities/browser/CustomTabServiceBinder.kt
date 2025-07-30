package com.dmdbrands.gurus.weight.core.shared.utilities.browser

import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import android.content.ComponentName
import android.content.Context

class CustomTabServiceBinder(
    private val context: Context,
    private val packageName: String,
    private val callback: CustomTabsCallback,
) {
    var session: CustomTabsSession? = null
        private set

    private var client: CustomTabsClient? = null
    private var isBound = false
    private var connection: CustomTabsServiceConnection? = null

    fun bind() {
        if (isBound) return

        connection =
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(
                    name: ComponentName,
                    client: CustomTabsClient,
                ) {
                    this@CustomTabServiceBinder.client = client
                    client.warmup(0L)
                    session = client.newSession(callback)
                    isBound = true
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    session = null
                    client = null
                    isBound = false
                }
            }

        CustomTabsClient.bindCustomTabsService(context, packageName, connection!!)
    }

    fun unbind() {
        connection?.let {
            context.unbindService(it)
            isBound = false
        }
    }
}
