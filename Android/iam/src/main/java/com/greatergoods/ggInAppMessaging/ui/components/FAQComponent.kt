package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.features.common.IAMText
import com.greatergoods.ggInAppMessaging.features.common.TextType
import com.greatergoods.ggInAppMessaging.features.resources.AppIcons
import com.greatergoods.ggInAppMessaging.theme.IamTheme

/**
 * FAQ content component for feedfaq route
 * Displays expandable FAQ items with questions and answers
 * Based on Figma design - content only, no header/navigation
 */
@Composable
fun FAQComponent(
  modifier: Modifier = Modifier
) {
  // Static FAQ data - no need for remember since it's static content
  val faqItems = listOf(
    FAQItem(
      id = "faq1",
      question = "How do I redeem the code on Amazon?",
      answer = "On the checkout page, look for an option to enter a gift card or promotional code under \"Payment Method\". Enter your coupon code and click \"Apply\".",
      imageUrl = AppIcons.PromoCode
    ),
    FAQItem(
      id = "faq2",
      question = "I have another question...",
      answer = "Send an email to hello@greatergoods.com. Our customer service team is happy to help.",
      imageUrl = null,
    ),
  )

  var expandedFaqIds by remember { mutableStateOf(setOf("faq1")) }

  LazyColumn(
    modifier = modifier
      .background(color = IamTheme.colors.secondaryBackground)
      .fillMaxWidth()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    items(faqItems) { faqItem ->
      FAQItemCard(
        faqItem = faqItem,
        isExpanded = expandedFaqIds.contains(faqItem.id),
        onToggle = {
          expandedFaqIds = if (expandedFaqIds.contains(faqItem.id)) {
            expandedFaqIds - faqItem.id
          } else {
            expandedFaqIds + faqItem.id
          }
        },
      )
    }
  }
}

/**
 * Individual FAQ item card with expandable content
 */
@Composable
private fun FAQItemCard(
  faqItem: FAQItem,
  isExpanded: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .background(color = IamTheme.colors.secondaryBackground),
    shape = RoundedCornerShape(8.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(
      modifier = Modifier
        .background(color = IamTheme.colors.secondaryBackground)
        .fillMaxWidth()
        .border(
          width = 1.dp,
          color = IamTheme.colors.utility,
          shape = RoundedCornerShape(8.dp),
        )
        .padding(16.dp),
    ) {
      // Question header with chevron
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onToggle() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IAMText(
          text = faqItem.question,
          textType = TextType.Subtitle2,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Icon(
          imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription = if (isExpanded) "Collapse" else "Expand",
          tint = IamTheme.colors.iconPrimary,
          modifier = Modifier.size(24.dp),
        )
      }

      // Expandable answer content
      AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(300)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        ) {
          // Answer text
          IAMText(
            text = faqItem.answer,
            textType = TextType.Body,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
          )

          if(faqItem.imageUrl != null){
            Spacer(modifier = Modifier.height(16.dp))
              AsyncImage(
                model = faqItem.imageUrl,
                contentDescription = "FAQ illustration",
                modifier = Modifier
                  .fillMaxSize()
                  .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
              )
          }
        }
      }
    }
  }
}

/**
 * FAQ item data class
 */
data class FAQItem(
  val id: String,
  val question: String,
  val answer: String,
  val imageUrl: Int? = null
)
