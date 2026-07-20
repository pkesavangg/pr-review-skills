package com.dmdbrands.gurus.weight.core.shared.utilities.conversion

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the MOB-715 contract for the height (cm) sent to a Bluetooth scale profile.
 *
 * Height is stored in tenths of an inch. The app displays imperial height by TRUNCATING to
 * whole inches (stored/10), while the scale re-derives ft/in from the cm we send by ROUNDING.
 * Sending the full-precision cm let the scale round up one inch (5'10" -> 5'11"). The fix
 * builds the imperial cm from the app's displayed whole inches so both agree, and keeps the
 * exact rounded cm for metric so the scale's cm display stays correct.
 */
class ConversionToolsHeightTest {

  // ---- Imperial: cm derived from the app's displayed whole inches ----

  @Test
  fun `imperial 5ft10in sends 178cm which the scale rounds back to 70in`() {
    // 5'10" = 70in = 700 tenths -> displayed 70in -> round(70 * 2.54) = 178
    val cm = ConversionTools.convertStoredHeightToScaleCm(700, isMetric = false)
    assertEquals(178.0, cm, 0.001)
    assertEquals(70L, Math.round(cm / 2.54)) // scale rounds 178cm back to 70in = 5'10"
  }

  @Test
  fun `imperial fractional-inch stored value uses the truncated inch (MOB-715 case)`() {
    // 71.7in stored (717) -> app shows 71in (5'11") -> send round(71 * 2.54) = 180 (not 182)
    val cm = ConversionTools.convertStoredHeightToScaleCm(717, isMetric = false)
    assertEquals(180.0, cm, 0.001)
    assertEquals(71L, Math.round(cm / 2.54)) // scale rounds 180cm to 71in = 5'11", matching the app
  }

  @Test
  fun `imperial 6ft0in sends 183cm rounding back to 72in`() {
    // 6'0" = 72in = 720 tenths -> round(72 * 2.54) = round(182.88) = 183
    val cm = ConversionTools.convertStoredHeightToScaleCm(720, isMetric = false)
    assertEquals(183.0, cm, 0.001)
    assertEquals(72L, Math.round(cm / 2.54))
  }

  // ---- Metric: exact rounded cm (unchanged behaviour) ----

  @Test
  fun `metric sends the exact rounded cm the app displays`() {
    // 182cm entered -> stored 717 -> app shows round(717 * 0.254) = 182cm -> send 182cm
    assertEquals(182.0, ConversionTools.convertStoredHeightToScaleCm(717, isMetric = true), 0.001)
    // 700 -> 177.8 -> 178
    assertEquals(178.0, ConversionTools.convertStoredHeightToScaleCm(700, isMetric = true), 0.001)
  }

  @Test
  fun `zero height maps to zero cm in both units`() {
    assertEquals(0.0, ConversionTools.convertStoredHeightToScaleCm(0, isMetric = false), 0.001)
    assertEquals(0.0, ConversionTools.convertStoredHeightToScaleCm(0, isMetric = true), 0.001)
  }
}
