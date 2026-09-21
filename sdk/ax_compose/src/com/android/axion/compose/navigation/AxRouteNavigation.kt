/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.axion.compose.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class AxRouteNavigator internal constructor(
    val route: String?,
    val isForward: Boolean,
    val canGoBack: Boolean,
    private val onNavigate: (String?, Boolean, Boolean) -> Unit,
    private val onBack: () -> Boolean,
) {
    fun navigateTo(route: String?) {
        onNavigate(route, true, false)
    }

    fun navigateToNested(route: String) {
        onNavigate(route, false, true)
    }

    fun replace(route: String?) {
        onNavigate(route, false, false)
    }

    fun goBack(): Boolean = onBack()
}

private data class AxRouteTransitionState(val route: String?, val isForward: Boolean)

@Composable
fun rememberAxRouteNavigator(
    initialRoute: String? = null,
    parentRoute: (String?) -> String? = { null },
): AxRouteNavigator {
    var route by rememberSaveable { mutableStateOf(initialRoute) }
    var backStack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var isForward by rememberSaveable { mutableStateOf(true) }
    val canGoBack = route != initialRoute || backStack.isNotEmpty() || parentRoute(route) != null

    return remember(route, backStack, isForward, parentRoute) {
        AxRouteNavigator(
            route = route,
            isForward = isForward,
            canGoBack = canGoBack,
            onNavigate = navigate@{ nextRoute, clearBackStack, pushCurrent ->
                if (nextRoute == route && !pushCurrent) {
                    return@navigate
                }
                if (clearBackStack) {
                    backStack = emptyList()
                } else if (pushCurrent) {
                    route?.let { backStack = backStack + it }
                }
                isForward = true
                route = nextRoute
            },
            onBack = {
                val parent = parentRoute(route)
                when {
                    parent != null -> {
                        isForward = false
                        route = parent
                        true
                    }
                    backStack.isNotEmpty() -> {
                        isForward = false
                        route = backStack.last()
                        backStack = backStack.dropLast(1)
                        true
                    }
                    route != initialRoute -> {
                        isForward = false
                        route = initialRoute
                        true
                    }
                    else -> false
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxRouteAnimatedContent(
    targetRoute: String?,
    isForward: Boolean,
    modifier: Modifier = Modifier,
    label: String = "axRouteTransition",
    content: @Composable (String?) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val transitionState = AxRouteTransitionState(targetRoute, isForward)
    AnimatedContent(
        targetState = transitionState,
        modifier = modifier,
        contentKey = { state -> state.route },
        transitionSpec = {
            val transform = if (targetState.isForward) {
                (slideInHorizontally(motionScheme.defaultSpatialSpec()) { it }
                        + fadeIn(motionScheme.defaultEffectsSpec()))
                    .togetherWith(
                        slideOutHorizontally(motionScheme.defaultSpatialSpec()) { -it / 3 }
                                + fadeOut(motionScheme.defaultEffectsSpec())
                    )
            } else {
                (slideInHorizontally(motionScheme.defaultSpatialSpec()) { -it / 3 }
                        + fadeIn(motionScheme.defaultEffectsSpec()))
                    .togetherWith(
                        slideOutHorizontally(motionScheme.defaultSpatialSpec()) { it }
                                + fadeOut(motionScheme.defaultEffectsSpec())
                    )
            }
            transform using null
        },
        label = label,
        content = { state -> content(state.route) },
    )
}
