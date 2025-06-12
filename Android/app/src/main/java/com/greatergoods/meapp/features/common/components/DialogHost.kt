package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.features.common.viewmodel.DialogQueueViewModel

@Composable
fun DialogHost() {
    val dialogQueueViewModel: DialogQueueViewModel = hiltViewModel()
    // Global dialog host
    DialogQueueHost(dialogQueueViewModel) { dialog ->
        // Custom dialog content can be provided here if needed
        when (dialog.contentKey) {
            "custom_dialog" -> {
                Text("Custom dialog: ${dialog.params}", modifier = Modifier.background(Red))
            }

            else -> {
                // Default dialog handling
                // This can be a placeholder or a default dialog implementation
            }
        }
    }
}
