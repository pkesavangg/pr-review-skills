package com.dmdbrands.gurus.weight.features.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.AppBottomSheet
import com.dmdbrands.gurus.weight.features.dashboard.strings.DashboardString
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * AHA (American Heart Association) blood-pressure rating reference sheet.
 * Opens when the user taps the BP chart header. Matches Figma node 26501:378159.
 *
 * Shows the five severity bands (Normal → Hypersensitive) with each band's color swatch
 * and the systolic/diastolic thresholds that define it.
 */
@Composable
fun BpAhaRatingsBottomSheet(
  onDismiss: () -> Unit,
) {
  AppBottomSheet(
    title = DashboardString.Bp.AhaRatings.Title,
    onDismiss = onDismiss,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MeTheme.colorScheme.secondaryBackground)
        .verticalScroll(rememberScrollState())
        .padding(vertical = MeTheme.spacing.md),
    ) {
      // Intro section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MeTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs),
      ) {
        Text(
          text = DashboardString.Bp.AhaRatings.SectionTitle,
          style = MeTheme.typography.heading4,
          color = MeTheme.colorScheme.textHeading,
        )
        Text(
          text = DashboardString.Bp.AhaRatings.SectionBody,
          style = MeTheme.typography.body2,
          color = MeTheme.colorScheme.textBody,
        )
      }

      Spacer(modifier = Modifier.height(MeTheme.spacing.md))

      AhaRatings.forEach { rating ->
        AhaRatingRow(rating = rating)
        Spacer(modifier = Modifier.height(MeTheme.spacing.md))
      }
    }
  }
}

@Composable
private fun AhaRatingRow(rating: AhaRating) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = MeTheme.spacing.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MeTheme.spacing.md),
  ) {
    // Color swatch
    Box(
      modifier = Modifier
        .size(width = 27.dp, height = 65.dp)
        .background(rating.color, RoundedCornerShape(4.dp)),
    )
    // Title + thresholds
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = rating.title,
        style = MeTheme.typography.heading4,
        color = MeTheme.colorScheme.textHeading,
      )
      Text(
        text = rating.systolic,
        style = MeTheme.typography.body2,
        color = MeTheme.colorScheme.textBody,
      )
      Text(
        text = rating.diastolic,
        style = MeTheme.typography.body2,
        color = MeTheme.colorScheme.textBody,
      )
    }
  }
}

/** One AHA band row — color swatch + text content for this file only. */
private data class AhaRating(
  val color: Color,
  val title: String,
  val systolic: String,
  val diastolic: String,
)

/**
 * The five AHA rating bands in Figma order (most severe → least severe).
 * Colors come directly from Figma node 26501:378159 — these are muted "indicator" tones
 * distinct from [SnapshotColors]'s brighter chart-line palette, so defined locally.
 */
private val AhaRatings: List<AhaRating> = listOf(
  AhaRating(
    color = Color(0xFFA60900),
    title = DashboardString.Bp.AhaRatings.HypersensitiveTitle,
    systolic = DashboardString.Bp.AhaRatings.HypersensitiveSystolic,
    diastolic = DashboardString.Bp.AhaRatings.HypersensitiveDiastolic,
  ),
  AhaRating(
    color = Color(0xFFC85000),
    title = DashboardString.Bp.AhaRatings.HypertensionStage2Title,
    systolic = DashboardString.Bp.AhaRatings.HypertensionStage2Systolic,
    diastolic = DashboardString.Bp.AhaRatings.HypertensionStage2Diastolic,
  ),
  AhaRating(
    color = Color(0xFFB57300),
    title = DashboardString.Bp.AhaRatings.HypertensionStage1Title,
    systolic = DashboardString.Bp.AhaRatings.HypertensionStage1Systolic,
    diastolic = DashboardString.Bp.AhaRatings.HypertensionStage1Diastolic,
  ),
  AhaRating(
    color = Color(0xFFA79300),
    title = DashboardString.Bp.AhaRatings.ElevatedTitle,
    systolic = DashboardString.Bp.AhaRatings.ElevatedSystolic,
    diastolic = DashboardString.Bp.AhaRatings.ElevatedDiastolic,
  ),
  AhaRating(
    color = Color(0xFF458239),
    title = DashboardString.Bp.AhaRatings.NormalTitle,
    systolic = DashboardString.Bp.AhaRatings.NormalSystolic,
    diastolic = DashboardString.Bp.AhaRatings.NormalDiastolic,
  ),
)
