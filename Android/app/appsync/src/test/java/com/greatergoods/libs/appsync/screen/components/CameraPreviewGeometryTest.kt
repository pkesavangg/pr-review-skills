package com.greatergoods.libs.appsync.screen.components

import com.greatergoods.libs.appsync.config.AppSyncConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the centre metering-region geometry used by the MOB-869 continuous
 * autofocus fix. Only the pure integer math is covered here — the Camera2 interop wiring
 * is framework code exercised on-device.
 */
class CameraPreviewGeometryTest {

  @Test
  fun `region is centred in an origin-anchored active array`() {
    // 4000x3000 sensor at (0,0), 0.15 half-fraction -> central 30% box.
    val bounds = centerRegionBounds(width = 4000, height = 3000, left = 0, top = 0, halfFraction = 0.15f)

    assertEquals(1400, bounds[0]) // x = 2000 - 600
    assertEquals(1050, bounds[1]) // y = 1500 - 450
    assertEquals(1200, bounds[2]) // width  = 600 * 2
    assertEquals(900, bounds[3]) // height = 450 * 2
  }

  @Test
  fun `region honours a non-zero active-array origin`() {
    val bounds = centerRegionBounds(width = 4000, height = 3000, left = 100, top = 50, halfFraction = 0.15f)

    assertEquals(1500, bounds[0]) // x = (100 + 2000) - 600
    assertEquals(1100, bounds[1]) // y = (50 + 1500) - 450
    assertEquals(1200, bounds[2])
    assertEquals(900, bounds[3])
  }

  @Test
  fun `region never starts before the active-array origin`() {
    // A half-fraction of 0.5 spans the whole axis; the clamp must keep x／y at the origin.
    val bounds = centerRegionBounds(width = 4000, height = 3000, left = 100, top = 50, halfFraction = 0.5f)

    assertEquals(100, bounds[0])
    assertEquals(50, bounds[1])
  }

  @Test
  fun `default fraction produces a non-empty centred box`() {
    val bounds =
      centerRegionBounds(
        width = 1920,
        height = 1080,
        left = 0,
        top = 0,
        halfFraction = AppSyncConstants.CENTER_REGION_HALF_FRACTION,
      )

    assertTrue(bounds[2] > 0, "width should be positive")
    assertTrue(bounds[3] > 0, "height should be positive")
    // Box stays inside the array on every side.
    assertTrue(bounds[0] >= 0 && bounds[0] + bounds[2] <= 1920)
    assertTrue(bounds[1] >= 0 && bounds[1] + bounds[3] <= 1080)
  }
}
