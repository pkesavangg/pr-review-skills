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
 * Extension property to provide ThemePreference DataStore instance from Context.
 */
val Context.themePreferenceStore: DataStore<ThemePreference> by dataStore(
    fileName = "theme_preference.pb",
    serializer = ThemePreferenceSerializer,
)

/**
 * DataStore for persisting the user's theme preference (light, dark, system).
 *
 * @constructor Creates a ThemeDataStore with the given context.
 * @param context The application context.
 */
class ThemeDataStore(
    context: Context,
) : BaseProtoDataStore<ThemePreference>(
        dataStore = context.themePreferenceStore,
    ) {
    /**
     * Clears the theme preference by resetting to SYSTEM.
     */
    override suspend fun clearData() {
        updateData { it.toBuilder().setMode(ThemeMode.SYSTEM).build() }
    }

    /**
     * Returns a [Flow] of the current theme mode.
     */
    val themeModeFlow: Flow<ThemeMode> = dataFlow.map { it.mode }

    /**
     * Updates the theme mode.
     * @param mode The new theme mode to set.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        updateData { it.toBuilder().setMode(mode).build() }
    }
}

/**
 * Serializer for ThemePreference proto.
 */
object ThemePreferenceSerializer : Serializer<ThemePreference> {
    override val defaultValue: ThemePreference =
        ThemePreference
            .newBuilder()
            .setMode(ThemeMode.SYSTEM)
            .build()

    override suspend fun readFrom(input: InputStream): ThemePreference = ThemePreference.parseFrom(input)

    override suspend fun writeTo(
        t: ThemePreference,
        output: OutputStream,
    ) = t.writeTo(output)
}
