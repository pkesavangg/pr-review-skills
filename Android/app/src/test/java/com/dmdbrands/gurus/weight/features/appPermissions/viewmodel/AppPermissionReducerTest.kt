package com.dmdbrands.gurus.weight.features.appPermissions.viewmodel

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AppPermissionReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class AppPermissionReducerTest {

    private lateinit var reducer: AppPermissionReducer

    companion object {
        private const val PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH"
        private const val PERMISSION_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        private const val PERMISSION_CAMERA = "android.permission.CAMERA"
    }

    @BeforeEach
    fun setUp() {
        reducer = AppPermissionReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default AppPermissionsState has expected initial values`() {
        val state = AppPermissionsState()

        assertThat(state.permissionMap).isEmpty()
        assertThat(state.requiredPermissions).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    // -------------------------------------------------------------------------
    // SetPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `SetPermissions updates permissionMap`() {
        val state = AppPermissionsState()
        val newMap: GGPermissionStatusMap = mutableMapOf()

        val result = reducer.reduce(state, AppPermissionsIntent.SetPermissions(newMap))

        assertThat(result.permissionMap).isSameInstanceAs(newMap)
    }

    @Test
    fun `SetPermissions replaces previous permissionMap`() {
        val oldMap: GGPermissionStatusMap = mutableMapOf()
        val newMap: GGPermissionStatusMap = mutableMapOf()
        val state = AppPermissionsState(permissionMap = oldMap)

        val result = reducer.reduce(state, AppPermissionsIntent.SetPermissions(newMap))

        assertThat(result.permissionMap).isSameInstanceAs(newMap)
        assertThat(result.permissionMap).isNotSameInstanceAs(oldMap)
    }

    @Test
    fun `SetPermissions preserves other state fields`() {
        val state = AppPermissionsState(
            requiredPermissions = setOf(PERMISSION_BLUETOOTH),
            isLoading = true,
            error = "some error",
        )
        val newMap: GGPermissionStatusMap = mutableMapOf()

        val result = reducer.reduce(state, AppPermissionsIntent.SetPermissions(newMap))

        assertThat(result.permissionMap).isSameInstanceAs(newMap)
        assertThat(result.requiredPermissions).containsExactly(PERMISSION_BLUETOOTH)
        assertThat(result.isLoading).isTrue()
        assertThat(result.error).isEqualTo("some error")
    }

    // -------------------------------------------------------------------------
    // SetRequiredPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `SetRequiredPermissions updates requiredPermissions`() {
        val state = AppPermissionsState()
        val permissions = setOf(PERMISSION_BLUETOOTH, PERMISSION_LOCATION)

        val result = reducer.reduce(
            state,
            AppPermissionsIntent.SetRequiredPermissions(permissions),
        )

        assertThat(result.requiredPermissions).containsExactly(
            PERMISSION_BLUETOOTH,
            PERMISSION_LOCATION,
        )
    }

    @Test
    fun `SetRequiredPermissions with empty set clears previous required permissions`() {
        val state = AppPermissionsState(
            requiredPermissions = setOf(PERMISSION_BLUETOOTH, PERMISSION_LOCATION),
        )

        val result = reducer.reduce(
            state,
            AppPermissionsIntent.SetRequiredPermissions(emptySet()),
        )

        assertThat(result.requiredPermissions).isEmpty()
    }

    @Test
    fun `SetRequiredPermissions preserves other state fields`() {
        val initialMap: GGPermissionStatusMap = mutableMapOf()
        val state = AppPermissionsState(
            permissionMap = initialMap,
            isLoading = false,
            error = null,
        )

        val result = reducer.reduce(
            state,
            AppPermissionsIntent.SetRequiredPermissions(setOf(PERMISSION_CAMERA)),
        )

        assertThat(result.requiredPermissions).containsExactly(PERMISSION_CAMERA)
        assertThat(result.permissionMap).isSameInstanceAs(initialMap)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents fall through to else -> state
    // -------------------------------------------------------------------------

    @Test
    fun `RequestPermission returns state unchanged by reducer`() {
        val state = AppPermissionsState(
            requiredPermissions = setOf(PERMISSION_BLUETOOTH),
        )

        val result = reducer.reduce(
            state,
            AppPermissionsIntent.RequestPermission(PERMISSION_BLUETOOTH),
        )

        assertThat(result).isEqualTo(state)
    }
}
