package com.greatergoods.meapp.core.navigation

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
import kotlinx.coroutines.flow.Flow


@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun NavigationObserver(
    navigationIntentFlow: Flow<NavigationIntent>,
    backStack: TopLevelBackStack<NavKey>,
) {
    val activity = LocalActivity.current

    LaunchedEffect(activity) {
        navigationIntentFlow
            .collect { intent ->
                when (intent) {
                    is NavigationIntent.NavigateTo -> {
                        backStack.add(intent.route)
                    }

                    is NavigationIntent.NavigateBack -> {
                        backStack.removeLast()
                    }

                    is NavigationIntent.NavigateToRoot -> {
                        (backStack).clearStack()
                    }

                    is NavigationIntent.NavigateToMultiple -> {

                        (backStack).addAll(intent.routes)

                    }

                    is NavigationIntent.ReplaceStack -> {
                        backStack.replaceStack(intent.routes)
                    }
                }
            }
    }
}
