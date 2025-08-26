package com.greatergoods.ggInAppMessaging.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.greatergoods.ggInAppMessaging.domain.constants.FeedStrings
import com.greatergoods.ggInAppMessaging.domain.constants.PromotionThemes
import com.greatergoods.ggInAppMessaging.domain.models.FeedItem
import com.greatergoods.ggInAppMessaging.domain.models.FeedActionType
import com.greatergoods.ggInAppMessaging.domain.models.FeedTypes
import com.greatergoods.ggInAppMessaging.core.utilities.FeedUtility
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feed landing page component with carousel and promo code functionality
 * Android equivalent of Angular feed-landing-page component
 */
@Composable
fun FeedLandingPageComponent(
    feedItem: FeedItem,
    isFromModal: Boolean = false,
    onDismiss: () -> Unit = {},
    onNavigateToFAQ: () -> Unit = {},
    onProductClick: (String, Int?) -> Unit = {},
    onPromoCodeCopy: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPromoCodeCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Get theme colors
    val themeColors = remember(feedItem.landingPage?.themeColor) {
        PromotionThemes.getThemeColors(feedItem.landingPage?.themeColor ?: "gray")
    }

    // Check if dark mode (you can integrate with your theme system)
    val isDarkMode = false // TODO: Integrate with your dark mode system

    val currentThemeColors = if (isDarkMode) {
        ThemeColors(
            promoCodeColor = themeColors.promoCodeBgColorDarkMode,
            promoCodeBgColor = themeColors.promoCodeBgColorDarkMode,
            copyButtonBgColor = themeColors.copyButtonBgColorDarkMode,
            promoCodeBgColorDarkMode = themeColors.promoCodeBgColorDarkMode,
            copyButtonBgColorDarkMode = themeColors.copyButtonBgColorDarkMode
        )
    } else {
        themeColors
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with back button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(currentThemeColors.promoCodeBgColor)
                        .padding(16.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = currentThemeColors.promoCodeColor
                        )
                    }

                    Text(
                        text = feedItem.messageTypeText,
                        style = MaterialTheme.typography.titleMedium,
                        color = currentThemeColors.promoCodeColor,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // FAQ button
                    IconButton(
                        onClick = onNavigateToFAQ,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = "FAQ",
                            style = MaterialTheme.typography.labelMedium,
                            color = currentThemeColors.promoCodeColor
                        )
                    }
                }

                // Main content
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Title image
                    AsyncImage(
                        model = feedItem.titleImage,
                        contentDescription = "Feed title image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = feedItem.titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    feedItem.subtitleModalText?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Landing page content
                    feedItem.landingPage?.let { landingPage ->
                        LandingPageContent(
                            landingPage = landingPage,
                            themeColors = currentThemeColors,
                            onProductClick = onProductClick,
                            onPromoCodeCopy = onPromoCodeCopy,
                            onPromoCodeCopied = { showPromoCodeCopied = true }
                        )
                    }
                }

                // Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = FeedStrings.Feeds.FeedLandingPage.COPY_RIGHTS,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    // Promo code copied toast
    if (showPromoCodeCopied) {
        LaunchedEffect(Unit) {
            delay(2000)
            showPromoCodeCopied = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Promo code copied!",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun LandingPageContent(
    landingPage: com.greatergoods.ggInAppMessaging.domain.models.LandingPage,
    themeColors: ThemeColors,
    onProductClick: (String, Int?) -> Unit,
    onPromoCodeCopy: (String) -> Unit,
    onPromoCodeCopied: () -> Unit
) {
    Column {
        // Promo code section
        landingPage.promoCode?.let { promoCode ->
            PromoCodeSection(
                promoCode = promoCode,
                themeColors = themeColors,
                onCopy = {
                    onPromoCodeCopy(promoCode)
                    onPromoCodeCopied()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Supporting content
        if (!landingPage.supportingTitleText.isNullOrEmpty() || !landingPage.supportingDescriptionText.isNullOrEmpty()) {
            SupportingContentSection(landingPage = landingPage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Featured products carousel
        landingPage.featuredProduct?.let { products ->
            if (products.isNotEmpty()) {
                FeaturedProductsCarousel(
                    products = products,
                    onProductClick = onProductClick
                )
            }
        }
    }
}

@Composable
private fun PromoCodeSection(
    promoCode: String,
    themeColors: ThemeColors,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = themeColors.promoCodeBgColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${FeedStrings.Feeds.FeedLandingPage.Promotion.MESSAGE_PREFIX}${FeedStrings.Feeds.FeedLandingPage.Promotion.CODE}${FeedStrings.Feeds.FeedLandingPage.Promotion.MESSAGE_SUFFIX}",
                style = MaterialTheme.typography.bodyMedium,
                color = themeColors.promoCodeColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FeedUtility.formatPromoCode(promoCode),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = themeColors.promoCodeColor,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.copyButtonBgColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = FeedStrings.Feeds.FeedLandingPage.COPY,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportingContentSection(
    landingPage: com.greatergoods.ggInAppMessaging.domain.models.LandingPage
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            landingPage.supportingTitleText?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            landingPage.supportingDescriptionText?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Supporting images carousel
            landingPage.supportingImage?.let { images ->
                if (images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SupportingImagesCarousel(images = images)
                }
            }
        }
    }
}

@Composable
private fun SupportingImagesCarousel(
    images: List<String>
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(images) { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = "Supporting image",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun FeaturedProductsCarousel(
    products: List<com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct>,
    onProductClick: (String, Int?) -> Unit
) {
    Column {
        Text(
            text = "Featured Products",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(products) { product ->
                FeaturedProductCard(
                    product = product,
                    onClick = { onProductClick(product.linkTarget, product.variationId) }
                )
            }
        }
    }
}

@Composable
private fun FeaturedProductCard(
    product: com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImage(
                model = product.productImage,
                contentDescription = "Product image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.titleText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = product.linkText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedLandingPageComponentPreview() {
    val sampleFeedItem = FeedItem(
        feedPostId = "sample-1",
        elementId = "element-1",
        accountId = "account-1",
        isUnread = true,
        messageTypeText = "Special Offer",
        titleText = "Get 20% Off",
        subtitleModalText = "Limited time offer on selected products",
        subtitleFeedText = "Get 20% off on selected items",
        titleImage = "https://example.com/image.jpg",
        feedType = FeedTypes.LANDING,
        landingPage = com.greatergoods.ggInAppMessaging.domain.models.LandingPage(
            feedLandingPageId = "landing-1",
            feedPostId = "sample-1",
            titleText = "Get 20% Off",
            promoCode = "SAVE20",
            themeColor = "blue",
            supportingTitleText = "Why Choose Us?",
            supportingDescriptionText = "Quality products at great prices",
            supportingImage = listOf("https://example.com/support1.jpg", "https://example.com/support2.jpg"),
            featuredProduct = listOf(
                com.greatergoods.ggInAppMessaging.domain.models.FeaturedProduct(
                    variationId = 1,
                    titleText = "Product 1",
                    feedLandingPageId = "landing-1",
                    linkText = "Shop Now",
                    linkTarget = "https://example.com/product1",
                    productImage = "https://example.com/product1.jpg"
                )
            )
        )
    )

    MaterialTheme {
        FeedLandingPageComponent(
            feedItem = sampleFeedItem,
            modifier = Modifier.padding(16.dp)
        )
    }
}
