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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.features.common.ButtonType
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.IamButton
import com.greatergoods.ggInAppMessaging.features.common.TextType
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
          //TODO:Image placeholder need to implement
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
            .padding(24.dp, 24.dp, 24.dp, 0.dp)
            .size(16.dp) // Larger button size
            .background(
              color = iamColors.primaryBackground,
              shape = CircleShape,
            ),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = iamColors.primaryAction,
            modifier = Modifier.size(16.dp),// Larger icon size
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
          IAMText(
            text = messageType.uppercase(),
            color = iamColors.textSubheading,
            textType = TextType.SubHeading,
            textAlign = TextAlign.Center,
          )
        }

        // Headline
        IAMText(
          text = headline,
          color = iamColors.textBody,
          textType = TextType.Title,
          textAlign = TextAlign.Center,
        )

        // Supporting Text
        IAMText(
          text = supportingText,
          color = iamColors.textBody,
          textType = TextType.Body,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.padding(top = 16.dp))

        // Action Buttons
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Primary Button
          IamButton (
            onClick = onPrimaryButtonClick,
            modifier = Modifier
              .height(40.dp)
              .padding(horizontal = 32.dp),
            type = ButtonType.PrimaryFilled,
            label = primaryButtonText
          )

          IamButton (
            onClick = onSecondaryButtonClick,
            modifier = Modifier
              .height(40.dp)
              .padding(horizontal = 32.dp),
            type = ButtonType.InlineTextPrimary,
            label = secondaryButtonText
          )
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
