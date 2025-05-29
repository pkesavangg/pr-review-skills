package com.greatergoods.meapp.core.navigation

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavBackStack
import com.greatergoods.meapp.domain.interfaces.NavigationIntent
import com.greatergoods.meapp.domain.interfaces.matchesBaseRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlin.reflect.KClass


@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun NavigationObserver(
    navigationIntentFlow: Flow<NavigationIntent>,
    backStack: NavBackStack,
    baseClass: KClass<out AppRoute>
) {
    val activity = LocalActivity.current

    LaunchedEffect(activity) {
        navigationIntentFlow
            .filter { intent ->
                intent.matchesBaseRoute(baseClass)
            }
            .collect { intent ->
                when (intent) {
                    is NavigationIntent.NavigateTo -> {
                        backStack.add(intent.route as AppRoute)
                    }

                    is NavigationIntent.NavigateBack -> {
                        backStack.removeLast()
                    }

                    is NavigationIntent.NavigateToRoot -> {
                        backStack.clear()
                    }

                    is NavigationIntent.NavigateToMultiple -> {
                        backStack.addAll(intent.routes.map { it as AppRoute })
                    }

                    is NavigationIntent.ReplaceStack -> {
                        backStack.clear()
                        backStack.addAll(intent.routes.map { it as AppRoute })
                    }
                }
            }
    }
}
