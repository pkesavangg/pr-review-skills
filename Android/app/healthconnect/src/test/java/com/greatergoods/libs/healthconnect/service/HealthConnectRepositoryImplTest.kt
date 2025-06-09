package com.greatergoods.libs.healthconnect.service

import androidx.health.connect.client.HealthConnectClient
import com.greatergoods.libs.healthconnect.IHealthConnect
import com.greatergoods.libs.healthconnect.enum.HealthConnectStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import android.content.Context

/**
 * Unit tests for [com.greatergoods.libs.healthconnect.IHealthConnect].
 */
class HealthConnectRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var repo: IHealthConnect

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        repo = IHealthConnect(context)
    }

    @Test
    fun testIsAvailable_unavailable() =
        runBlocking {
            Mockito.mockStatic(HealthConnectClient::class.java).use { mocked ->
                mocked
                    .`when`<Int> { HealthConnectClient.getSdkStatus(context) }
                    .thenReturn(HealthConnectClient.SDK_UNAVAILABLE)
                assertEquals(false, repo.isAvailable())
            }
        }

    @Test
    fun testGetStatus_installed() =
        runBlocking {
            Mockito.mockStatic(HealthConnectClient::class.java).use { mocked ->
                mocked
                    .`when`<Int> { HealthConnectClient.getSdkStatus(context) }
                    .thenReturn(HealthConnectClient.SDK_AVAILABLE)
                assertEquals(HealthConnectStatus.INSTALLED, repo.getStatus())
            }
        }
}
