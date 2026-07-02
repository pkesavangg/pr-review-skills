package com.dmdbrands.gurus.weight.features.DeviceUsers.reducer

import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DeviceUserListReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class DeviceUserListReducerTest {

    private lateinit var reducer: DeviceUserListReducer

    private val fakeDevice: Device = Device(
        id = "device-1",
        connectionStatus = BLEStatus.CONNECTED,
        nickname = "My Scale",
    )

    private val fakeUser1 = GGBTUser(
        name = "Alice",
        token = "token-1",
        lastActive = 1000L,
        isBodyMetricsEnabled = true,
    )

    private val fakeUser2 = GGBTUser(
        name = "Bob",
        token = "token-2",
        lastActive = 2000L,
        isBodyMetricsEnabled = false,
    )

    @BeforeEach
    fun setUp() {
        reducer = DeviceUserListReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default DeviceUserListState has expected initial values`() {
        val state = DeviceUserListState()

        assertThat(state.scale).isNull()
        assertThat(state.scaleUserList).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasSetUsername).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetScale
    // -------------------------------------------------------------------------

    @Test
    fun `SetScale updates scale in state`() {
        val state = DeviceUserListState()

        val result = reducer.reduce(state, DeviceUserListIntent.SetScale(fakeDevice))

        assertThat(result?.scale).isEqualTo(fakeDevice)
    }

    @Test
    fun `SetScale sets hasSetUsername when provided`() {
        val state = DeviceUserListState(hasSetUsername = false)

        val result = reducer.reduce(state, DeviceUserListIntent.SetScale(fakeDevice, hasSetUsername = true))

        assertThat(result?.hasSetUsername).isTrue()
    }

    @Test
    fun `SetScale hasSetUsername defaults to false`() {
        val state = DeviceUserListState(hasSetUsername = true)

        val result = reducer.reduce(state, DeviceUserListIntent.SetScale(fakeDevice))

        assertThat(result?.hasSetUsername).isFalse()
    }

    @Test
    fun `SetScale preserves scaleUserList and isLoading`() {
        val state = DeviceUserListState(
            scaleUserList = listOf(fakeUser1).toImmutableList(),
            isLoading = true,
        )

        val result = reducer.reduce(state, DeviceUserListIntent.SetScale(fakeDevice))

        assertThat(result?.scaleUserList).containsExactly(fakeUser1)
        assertThat(result?.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetUserList
    // -------------------------------------------------------------------------

    @Test
    fun `SetUserList populates scaleUserList`() {
        val state = DeviceUserListState()

        val result = reducer.reduce(state, DeviceUserListIntent.SetUserList(listOf(fakeUser1, fakeUser2)))

        assertThat(result?.scaleUserList).containsExactly(fakeUser1, fakeUser2).inOrder()
    }

    @Test
    fun `SetUserList clears isLoading`() {
        val state = DeviceUserListState(isLoading = true)

        val result = reducer.reduce(state, DeviceUserListIntent.SetUserList(listOf(fakeUser1)))

        assertThat(result?.isLoading).isFalse()
    }

    @Test
    fun `SetUserList with empty list clears scaleUserList`() {
        val state = DeviceUserListState(scaleUserList = listOf(fakeUser1).toImmutableList())

        val result = reducer.reduce(state, DeviceUserListIntent.SetUserList(emptyList()))

        assertThat(result?.scaleUserList).isEmpty()
    }

    @Test
    fun `SetUserList preserves scale and hasSetUsername`() {
        val state = DeviceUserListState(scale = fakeDevice, hasSetUsername = true)

        val result = reducer.reduce(state, DeviceUserListIntent.SetUserList(listOf(fakeUser1)))

        assertThat(result?.scale).isEqualTo(fakeDevice)
        assertThat(result?.hasSetUsername).isTrue()
    }

    // -------------------------------------------------------------------------
    // UpdateFormWithUserList
    // -------------------------------------------------------------------------

    @Test
    fun `UpdateFormWithUserList updates usernameForm`() {
        val state = DeviceUserListState()

        val result = reducer.reduce(
            state,
            DeviceUserListIntent.UpdateFormWithUserList(listOf(fakeUser1, fakeUser2)),
        )

        assertThat(result).isNotNull()
        assertThat(result?.usernameForm).isNotNull()
    }

    @Test
    fun `UpdateFormWithUserList preserves current username value`() {
        val state = DeviceUserListState()
        // Set an initial username value via the form controls
        state.usernameForm.username.onValueChange("Charlie")

        val result = reducer.reduce(
            state,
            DeviceUserListIntent.UpdateFormWithUserList(listOf(fakeUser1)),
        )

        assertThat(result?.usernameForm?.username?.value).isEqualTo("Charlie")
    }

    @Test
    fun `UpdateFormWithUserList preserves scale and hasSetUsername`() {
        val state = DeviceUserListState(scale = fakeDevice, hasSetUsername = true)

        val result = reducer.reduce(
            state,
            DeviceUserListIntent.UpdateFormWithUserList(listOf(fakeUser1)),
        )

        assertThat(result?.scale).isEqualTo(fakeDevice)
        assertThat(result?.hasSetUsername).isTrue()
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    @Test
    fun `Save sets isLoading to true`() {
        val state = DeviceUserListState(isLoading = false)

        val result = reducer.reduce(state, DeviceUserListIntent.Save)

        assertThat(result?.isLoading).isTrue()
    }

    @Test
    fun `Save preserves scale and scaleUserList`() {
        val state = DeviceUserListState(
            scale = fakeDevice,
            scaleUserList = listOf(fakeUser1).toImmutableList(),
        )

        val result = reducer.reduce(state, DeviceUserListIntent.Save)

        assertThat(result?.scale).isEqualTo(fakeDevice)
        assertThat(result?.scaleUserList).containsExactly(fakeUser1)
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — fall through to else -> state.copy()
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteUser returns state unchanged`() {
        val state = DeviceUserListState(
            scaleUserList = listOf(fakeUser1, fakeUser2).toImmutableList(),
        )

        val result = reducer.reduce(state, DeviceUserListIntent.DeleteUser(fakeUser1))

        assertThat(result?.scaleUserList).containsExactly(fakeUser1, fakeUser2).inOrder()
    }

    @Test
    fun `RefreshUserList returns state unchanged`() {
        val state = DeviceUserListState(scale = fakeDevice, isLoading = false)

        val result = reducer.reduce(state, DeviceUserListIntent.RefreshUserList)

        assertThat(result?.scale).isEqualTo(fakeDevice)
        assertThat(result?.isLoading).isFalse()
    }

    @Test
    fun `Back returns state unchanged`() {
        val state = DeviceUserListState(hasSetUsername = true, scale = fakeDevice)

        val result = reducer.reduce(state, DeviceUserListIntent.Back)

        assertThat(result?.hasSetUsername).isTrue()
        assertThat(result?.scale).isEqualTo(fakeDevice)
    }
}
