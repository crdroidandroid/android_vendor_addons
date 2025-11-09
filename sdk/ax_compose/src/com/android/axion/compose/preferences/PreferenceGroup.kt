/*
 * Copyright (C) 2025-2026 AxionOS Project
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

package com.android.axion.compose.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

interface PreferenceGroupScope {
    fun item(content: @Composable () -> Unit)
}

class PreferenceGroupScopeImpl : PreferenceGroupScope {
    private val items = mutableListOf<@Composable () -> Unit>()

    override fun item(content: @Composable () -> Unit) {
        items.add(content)
    }

    fun getItems(): List<@Composable () -> Unit> = items
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: PreferenceGroupScope.() -> Unit
) {
    val scope = PreferenceGroupScopeImpl()
    scope.content()
    val items: List<@Composable () -> Unit> = scope.getItems()

    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chevron",
    )

    val containerModifier = modifier

    Column(modifier = containerModifier) {
        if (title != null) {
            if (collapsible) {
                val titleShape = if (expanded) {
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 4.dp,
                    )
                } else {
                    RoundedCornerShape(28.dp)
                }
                Row(
                    modifier = Modifier
                        .padding(bottom = if (expanded) 1.dp else 0.dp)
                        .fillMaxWidth()
                        .clip(titleShape)
                        .background(MaterialTheme.colorScheme.surfaceBright)
                        .clickable { expanded = !expanded }
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(360.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, bottom = 8.dp, top = 16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = !collapsible || expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items.forEachIndexed { index, composable ->
                    val position = when {
                        items.size == 1 -> PreferencePosition.Single
                        index == 0 -> if (collapsible && title != null) PreferencePosition.Middle else PreferencePosition.Top
                        index == items.size - 1 -> PreferencePosition.Bottom
                        else -> PreferencePosition.Middle
                    }
                    CompositionLocalProvider(LocalPreferencePosition provides position) {
                        composable()
                    }
                }
            }
        }
    }
}
