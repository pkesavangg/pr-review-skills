package com.greatergoods.meapp.data.storage.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import android.content.Context

/**
 * Extension property to provide FcmToken DataStore instance from Context.
 */
val Context.fcmTokenStore: DataStore<FcmToken> by dataStore(
    fileName = "fcm_token.pb",
    serializer = FcmTokenSerializer,
)

/**
 * DataStore for persisting the FCM token.
 *
 * @constructor Creates a FcmTokenDataStore with the given context.
 * @param context The application context.
 */
class FcmDataStore(context: Context) : BaseProtoDataStore<FcmToken>(
    dataStore = context.fcmTokenStore,
) {
    /**
     * Gets the default instance of FcmToken.
     */
    override fun getDefaultInstance(): FcmToken = FcmToken.getDefaultInstance()

    /**
     * Optional: Override clearData() only if you need custom clear logic
     * Otherwise, the base implementation will use getDefaultInstance()
     */
    override suspend fun clearData() {
        super.clearData() // Use the base implementation
    }

    /**
     * Returns a [Flow] of the current FCM token string.
     */
    val tokenFlow: Flow<String> = dataFlow.map { it.token }

    /**
     * Updates the FCM token.
     * @param token The new FCM token to set.
     */
    suspend fun setToken(token: String) {
        updateData { it.toBuilder().setToken(token).build() }
    }
}

/**
 * Serializer for FcmToken proto.
 */
object FcmTokenSerializer : Serializer<FcmToken> {
    override val defaultValue: FcmToken = FcmToken.newBuilder()
        .setToken("")
        .build()

    override suspend fun readFrom(input: InputStream): FcmToken =
        FcmToken.parseFrom(input)

    override suspend fun writeTo(t: FcmToken, output: OutputStream) =
        t.writeTo(output)
}
