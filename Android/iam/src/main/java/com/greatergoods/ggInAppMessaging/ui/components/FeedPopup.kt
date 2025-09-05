package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.theme.IamTheme
import com.greatergoods.ggInAppMessaging.theme.ProvideIamTheme
import com.greatergoods.ggInAppMessaging.ui.strings.FeedPopupStrings

/**
 * Feed Popup Modal Component
 * Displays promotional content with image, headline, supporting text, and action buttons
 * Based on Figma design: https://www.figma.com/design/W7fZI4HJ9QC95NamHi92BG/Me.Health-Mega-App?node-id=8369-249535&m=dev
 */
@Composable
fun FeedPopup(
  imageUrl: String? = null,
  messageType: String = "",
  headline: String,
  supportingText: String,
  primaryButtonText: String = FeedPopupStrings.ShopNow,
  secondaryButtonText: String = FeedPopupStrings.MessageSettings,
  onPrimaryButtonClick: () -> Unit = {},
  onSecondaryButtonClick: () -> Unit = {},
  onCloseClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val iamColors = IamTheme.colors

  Card(
    modifier = modifier
      .width(312.dp)
      .clip(RoundedCornerShape(28.dp)),
    colors = CardDefaults.cardColors(
      containerColor = iamColors.primaryBackground,
    ),
    elevation = CardDefaults.cardElevation(
      defaultElevation = 8.dp,
    ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      // Header with Image and Close Button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(220.dp),
      ) {
        // Product Image
        if (imageUrl != null) {
          AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
              .fillMaxWidth()
              .height(220.dp)
              .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            contentScale = ContentScale.Crop,
          )
        } else {
          // Fallback background
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(220.dp)
              .background(iamColors.utility),
          )
        }

        // Close Button
        IconButton(
          onClick = onCloseClick,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(24.dp, 24.dp, 0.dp, 0.dp)
            .size(24.dp)
            .background(
              color = iamColors.primaryBackground,
              shape = CircleShape,
            ),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = iamColors.primaryAction,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      // Content Section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Message Type Label
        if (messageType.isNotEmpty()) {
          Text(
            text = messageType.uppercase(),
            color = iamColors.textSubheading,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        }

        // Headline
        Text(
          text = headline,
          color = iamColors.textBody,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          lineHeight = 30.sp,
          modifier = Modifier.fillMaxWidth(),
        )

        // Supporting Text
        Text(
          text = supportingText,
          color = iamColors.textBody,
          fontSize = 16.sp,
          fontWeight = FontWeight.Normal,
          textAlign = TextAlign.Center,
          lineHeight = 22.sp,
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Primary Button
          Button(
            onClick = onPrimaryButtonClick,
            modifier = Modifier
              .height(40.dp)
              .padding(horizontal = 32.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = iamColors.primaryAction,
            ),
            shape = RoundedCornerShape(999.dp),
          ) {
            Text(
              text = primaryButtonText.uppercase(),
              color = iamColors.primaryBackground,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
            )
          }

          // Secondary Button
          TextButton(
            onClick = onSecondaryButtonClick,
            modifier = Modifier
              .height(40.dp)
              .padding(horizontal = 32.dp),
          ) {
            Text(
              text = secondaryButtonText.uppercase(),
              color = iamColors.primaryAction,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Preview
@Composable
fun FeedPopupPreview() {
  ProvideIamTheme {
    FeedPopup(
      imageUrl = null,
      messageType = "LIGHTNING DEAL",
      headline = "Here's a headline that's 40 characters.",
      supportingText = "Supporting text that can be customized up to 60 characters.",
      primaryButtonText = "SHOP NOW",
      secondaryButtonText = "MESSAGE SETTINGS",
    )
  }
}
