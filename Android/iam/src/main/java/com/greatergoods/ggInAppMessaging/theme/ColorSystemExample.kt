package com.greatergoods.ggInAppMessaging.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.ggInAppMessaging.theme.getIAMColors

/**
 * Example usage of the IAM color system
 * This file demonstrates how to use the color tokens and schemes
 */
@Composable
fun IAMColorSystemExample() {
    // Get IAM colors from MaterialTheme
    val iamColors = getIAMColors()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iamColors.backgroundPrimary)
            .padding(16.dp)
    ) {
        // Example of using semantic colors
        Text(
            text = "IAM Color System Example",
            style = MaterialTheme.typography.headlineLarge,
            color = iamColors.textHeading
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Example of using action colors
        Button(
            onClick = { /* action */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = iamColors.actionPrimary
            )
        ) {
            Text(
                text = "Primary Action",
                color = iamColors.actionInverse
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Example of using status colors
        Card(
            colors = CardDefaults.cardColors(
                containerColor = iamColors.statusSuccess
            )
        ) {
            Text(
                text = "Success Status",
                color = iamColors.actionInverse,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Example of using theme colors
        val themeColor = iamColors.getThemeColor("blue")
        Card(
            colors = CardDefaults.cardColors(
                containerColor = themeColor
            )
        ) {
            Text(
                text = "Blue Theme",
                color = iamColors.actionInverse,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Example of manual color scheme creation
 */
@Composable
fun ManualColorSchemeExample() {
    // Create color scheme for specific theme mode
    val lightModeColors = IAMColorScheme(isDarkMode = false)
    val darkModeColors = IAMColorScheme(isDarkMode = true)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightModeColors.backgroundPrimary)
            .padding(16.dp)
    ) {
        Text(
            text = "Light Mode Colors",
            color = lightModeColors.textHeading
        )
        
        Button(
            onClick = { /* action */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = lightModeColors.actionPrimary
            )
        ) {
            Text("Light Mode Button", color = lightModeColors.actionInverse)
        }
    }
}
