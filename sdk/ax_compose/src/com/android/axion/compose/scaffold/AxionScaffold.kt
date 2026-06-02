/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.axion.compose.scaffold

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp as lerpTextStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt

private val MaxHeight = 179.dp
private val PinnedHeight = 56.dp
private val LargeTitleBottomPadding = 28.dp
private val HorizontalPadding = 4.dp
private val NavIconPaddingStart = 20.dp
private val NavIconPaddingEnd = 16.dp
private val ExpandedTitlePaddingStart = 24.dp
private val TitlePaddingEnd = 16.dp

private val safeDrawingWindowInsets: WindowInsets
    @Composable
    get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxionScaffold(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    collapsedByDefault: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        topBar = {
            AxionLargeTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                collapsedByDefault = collapsedByDefault,
                containerColor = containerColor,
                navigationIcon = { ExpressiveBackButton(onClick = onBackClick) },
                actions = actions,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxionPinnedTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    scrolledContainerColor: Color = containerColor,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    navigationIconContentColor: Color = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor: Color = MaterialTheme.colorScheme.primary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1f),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = scrolledContainerColor,
            titleContentColor = titleContentColor,
            navigationIconContentColor = navigationIconContentColor,
            actionIconContentColor = actionIconContentColor,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxionLargeTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    collapsedByDefault: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    navigationIcon: @Composable () -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AxionTopAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = containerColor,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.primary,
    )

    val expandedTextStyle =
        MaterialTheme.typography.displaySmallEmphasized.copy(textMotion = TextMotion.Animated)
    val collapsedTextStyle =
        MaterialTheme.typography.titleLargeEmphasized.copy(textMotion = TextMotion.Animated)

    val density = LocalDensity.current
    val pinnedHeightPx = density.run { PinnedHeight.toPx() }
    val titleBottomPaddingPx = density.run { LargeTitleBottomPadding.roundToPx() }
    val maxHeightPx = density.run { MaxHeight.toPx() }
    val heightOffsetLimit = pinnedHeightPx - maxHeightPx

    LaunchedEffect(heightOffsetLimit) { scrollBehavior.state.heightOffsetLimit = heightOffsetLimit }

    if (collapsedByDefault) {
        var hasCollapsedInitially by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(scrollBehavior) {
            if (!hasCollapsedInitially) {
                with(scrollBehavior.state) { heightOffset = heightOffsetLimit }
                hasCollapsedInitially = true
            }
        }
    }

    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val appBarContainerColor = colors.containerColor(collapsedFraction)

    val actionsRow = @Composable {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }

    val appBarDragModifier =
        if (!scrollBehavior.isPinned) {
            Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scrollBehavior.state.heightOffset += delta
                },
                onDragStopped = { velocity ->
                    settleAppBar(
                        scrollBehavior.state,
                        velocity,
                        scrollBehavior.flingAnimationSpec,
                        scrollBehavior.snapAnimationSpec,
                    )
                },
            )
        } else {
            Modifier
        }

    val currentAppBarHeightPx = maxHeightPx + scrollBehavior.state.heightOffset
    val interpolatedTextStyle = lerpTextStyle(expandedTextStyle, collapsedTextStyle, collapsedFraction)
    val navIconPaddingStartPx = density.run { NavIconPaddingStart.toPx() }
    val navIconPaddingEndPx = density.run { NavIconPaddingEnd.toPx() }
    val expandedTitlePaddingStartPx = density.run { ExpandedTitlePaddingStart.toPx() }
    val currentMaxLines = if (collapsedFraction < 0.5f) 3 else 1

    Box(
        modifier = modifier
            .then(appBarDragModifier)
            .drawBehind { drawRect(color = appBarContainerColor) }
            .semantics { isTraversalGroup = true }
            .pointerInput(Unit) {}
    ) {
        Layout(
            content = {
                Box(Modifier.layoutId("navigationIcon")) {
                    CompositionLocalProvider(
                        LocalContentColor provides colors.navigationIconContentColor,
                    ) {
                        navigationIcon()
                    }
                }

                Box(Modifier.layoutId("title")) {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        CompositionLocalProvider(
                            LocalDensity provides Density(
                                density = density.density,
                                fontScale = 1f,
                            )
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier
                                    .padding(end = TitlePaddingEnd)
                                    .semantics { heading() },
                                color = colors.titleContentColor,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = currentMaxLines,
                                style = interpolatedTextStyle,
                            )
                        }
                    }
                }

                Box(Modifier.layoutId("actionIcons").padding(end = HorizontalPadding)) {
                    CompositionLocalProvider(
                        LocalContentColor provides colors.actionIconContentColor,
                        content = actionsRow,
                    )
                }
            },
            modifier = Modifier.windowInsetsPadding(safeDrawingWindowInsets).clipToBounds(),
        ) { measurables, constraints ->
            val navigationIconPlaceable = measurables
                .first { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0))
            val actionIconsPlaceable = measurables
                .first { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0))

            val navigationIconWidth = navigationIconPlaceable.width
            val collapsedTitlePaddingStartPx =
                if (navigationIconWidth > 0) {
                    navIconPaddingStartPx + navigationIconWidth + navIconPaddingEndPx
                } else {
                    expandedTitlePaddingStartPx
                }
            val interpolatedPaddingStartPx = lerp(
                start = expandedTitlePaddingStartPx,
                stop = collapsedTitlePaddingStartPx,
                fraction = collapsedFraction,
            )
            val titleHorizontalPaddingPx = interpolatedPaddingStartPx + density.run { TitlePaddingEnd.toPx() }
            val titleMaxWidth = (constraints.maxWidth -
                    collapsedFraction * actionIconsPlaceable.width -
                    titleHorizontalPaddingPx)
                .roundToInt()
                .coerceAtLeast(0)

            val titlePlaceable = measurables
                .first { it.layoutId == "title" }
                .measure(Constraints.fixedWidth(titleMaxWidth))

            val layoutWidth = constraints.maxWidth
            val layoutHeight = currentAppBarHeightPx.roundToInt().coerceAtLeast(0)

            layout(layoutWidth, layoutHeight) {
                navigationIconPlaceable.placeRelative(
                    x = navIconPaddingStartPx.roundToInt(),
                    y = ((pinnedHeightPx - navigationIconPlaceable.height) / 2f).roundToInt(),
                )

                actionIconsPlaceable.placeRelative(
                    x = layoutWidth - actionIconsPlaceable.width,
                    y = ((pinnedHeightPx - actionIconsPlaceable.height) / 2f).roundToInt(),
                )

                val titleYCollapsed = (pinnedHeightPx - titlePlaceable.height) / 2f
                val titleYExpanded = maxHeightPx - titlePlaceable.height - titleBottomPaddingPx
                val interpolatedTitleY = lerp(titleYExpanded, titleYCollapsed, collapsedFraction)

                titlePlaceable.placeRelative(
                    x = interpolatedPaddingStartPx.roundToInt(),
                    y = interpolatedTitleY.roundToInt(),
                )
            }
        }
    }
}

@Stable
private class AxionTopAppBarColors(
    val containerColor: Color,
    val scrolledContainerColor: Color,
    val navigationIconContentColor: Color,
    val titleContentColor: Color,
    val actionIconContentColor: Color,
) {
    @Stable
    fun containerColor(colorTransitionFraction: Float): Color = lerp(
        containerColor,
        scrolledContainerColor,
        FastOutLinearInEasing.transform(colorTransitionFraction),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        shape = IconButtonDefaults.smallRoundShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCollapsedScrollBehavior(): TopAppBarScrollBehavior {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CollapseOnFirstComposition(scrollBehavior)
    return scrollBehavior
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapseOnFirstComposition(scrollBehavior: TopAppBarScrollBehavior) {
    val heightOffsetLimit = scrollBehavior.state.heightOffsetLimit
    var hasCollapsedInitially by rememberSaveable(heightOffsetLimit) { mutableStateOf(false) }
    LaunchedEffect(heightOffsetLimit) {
        if (!hasCollapsedInitially) {
            with(scrollBehavior.state) { heightOffset = heightOffsetLimit }
            hasCollapsedInitially = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleAppBar(
    state: TopAppBarState,
    velocity: Float,
    flingAnimationSpec: DecayAnimationSpec<Float>?,
    snapAnimationSpec: AnimationSpec<Float>?,
): Velocity {
    if (state.collapsedFraction < 0.01f || state.collapsedFraction == 1f) {
        return Velocity.Zero
    }
    var remainingVelocity = velocity
    if (flingAnimationSpec != null && abs(velocity) > 1f) {
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(
            flingAnimationSpec
        ) {
            val delta = value - lastValue
            val initialHeightOffset = state.heightOffset
            state.heightOffset = initialHeightOffset + delta
            val consumed = abs(initialHeightOffset - state.heightOffset)
            lastValue = value
            remainingVelocity = this.velocity
            if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
        }
    }
    if (snapAnimationSpec != null) {
        if (state.heightOffset < 0 && state.heightOffset > state.heightOffsetLimit) {
            AnimationState(initialValue = state.heightOffset).animateTo(
                if (state.collapsedFraction < 0.5f) 0f else state.heightOffsetLimit,
                animationSpec = snapAnimationSpec,
            ) {
                state.heightOffset = value
            }
        }
    }
    return Velocity(0f, remainingVelocity)
}
