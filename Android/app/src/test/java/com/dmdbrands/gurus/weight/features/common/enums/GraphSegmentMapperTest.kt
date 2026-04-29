package com.dmdbrands.gurus.weight.features.common.enums

import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.features.settings.strings.toDisplayString
import com.dmdbrands.gurus.weight.proto.DefaultGraphSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphSegmentMapperTest {

  @Test
  fun `proto WEEK maps to GraphSegment WEEK`() {
    assertEquals(GraphSegment.WEEK, DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_WEEK.toGraphSegment())
  }

  @Test
  fun `proto MONTH maps to GraphSegment MONTH`() {
    assertEquals(GraphSegment.MONTH, DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH.toGraphSegment())
  }

  @Test
  fun `proto YEAR maps to GraphSegment YEAR`() {
    assertEquals(GraphSegment.YEAR, DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_YEAR.toGraphSegment())
  }

  @Test
  fun `proto TOTAL maps to GraphSegment TOTAL`() {
    assertEquals(GraphSegment.TOTAL, DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_TOTAL.toGraphSegment())
  }

  // The fresh-install path: when an account has never set a default, the proto field is
  // serialised as UNSPECIFIED. We map that to MONTH per the AC.
  @Test
  fun `proto UNSPECIFIED falls back to GraphSegment MONTH for fresh installs`() {
    assertEquals(
      GraphSegment.MONTH,
      DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_UNSPECIFIED.toGraphSegment(),
    )
  }

  @Test
  fun `proto UNRECOGNIZED falls back to GraphSegment MONTH`() {
    assertEquals(GraphSegment.MONTH, DefaultGraphSegment.UNRECOGNIZED.toGraphSegment())
  }

  @Test
  fun `GraphSegment WEEK maps back to proto WEEK`() {
    assertEquals(DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_WEEK, GraphSegment.WEEK.toDefaultGraphSegment())
  }

  @Test
  fun `GraphSegment MONTH maps back to proto MONTH`() {
    assertEquals(DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_MONTH, GraphSegment.MONTH.toDefaultGraphSegment())
  }

  @Test
  fun `GraphSegment YEAR maps back to proto YEAR`() {
    assertEquals(DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_YEAR, GraphSegment.YEAR.toDefaultGraphSegment())
  }

  @Test
  fun `GraphSegment TOTAL maps back to proto TOTAL`() {
    assertEquals(DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_TOTAL, GraphSegment.TOTAL.toDefaultGraphSegment())
  }

  @Test
  fun `round-trip preserves all GraphSegment values`() {
    GraphSegment.entries.forEach { segment ->
      assertEquals(segment, segment.toDefaultGraphSegment().toGraphSegment())
    }
  }

  @Test
  fun `GraphSegment DEFAULT is MONTH per the AC`() {
    assertEquals(GraphSegment.MONTH, GraphSegment.DEFAULT)
  }

  @Test
  fun `proto UNSPECIFIED falls back to GraphSegment DEFAULT`() {
    // Re-asserts the fresh-install path via the centralised constant — guards against drift
    // if DEFAULT is ever rebound to a different value.
    assertEquals(
      GraphSegment.DEFAULT,
      DefaultGraphSegment.DEFAULT_GRAPH_SEGMENT_UNSPECIFIED.toGraphSegment(),
    )
  }

  @Test
  fun `toDisplayString WEEK renders the Week label`() {
    assertEquals(RadioGroupModalStrings.DefaultGraphRange.Week, GraphSegment.WEEK.toDisplayString())
  }

  @Test
  fun `toDisplayString MONTH renders the Month label`() {
    assertEquals(RadioGroupModalStrings.DefaultGraphRange.Month, GraphSegment.MONTH.toDisplayString())
  }

  @Test
  fun `toDisplayString YEAR renders the Year label`() {
    assertEquals(RadioGroupModalStrings.DefaultGraphRange.Year, GraphSegment.YEAR.toDisplayString())
  }

  @Test
  fun `toDisplayString TOTAL renders the Total label`() {
    assertEquals(RadioGroupModalStrings.DefaultGraphRange.Total, GraphSegment.TOTAL.toDisplayString())
  }

  @Test
  fun `toDisplayString covers all GraphSegment values`() {
    GraphSegment.entries.forEach { segment ->
      // Smoke-test: every enum member must produce a non-empty label so the modal never
      // renders a blank row if a new segment is added without a label binding.
      val label = segment.toDisplayString()
      assert(label.isNotBlank()) { "toDisplayString returned blank for $segment" }
    }
  }
}
