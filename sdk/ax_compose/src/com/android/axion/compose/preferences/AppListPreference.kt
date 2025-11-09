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

package com.android.axion.compose.preferences

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppInfo(val packageName: String, val label: String, val icon: Drawable?)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppListPreference(
    settingKey: String,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    emptyText: String = "No apps selected",
    addButtonText: String = "Add apps",
    onAddClicked: (currentPackages: Set<String>) -> Unit,
    position: PreferencePosition = LocalPreferencePosition.current,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val shape = preferenceShape(position)

    val flow = rememberSettingsFlow(SettingsType.SECURE)
    val savedString by rememberSettingString(settingKey, SettingsType.SECURE)
    val selectedPackages = remember(savedString) {
        if (savedString.isEmpty()) emptySet()
        else savedString.split(",").filter { it.isNotEmpty() }.toSet()
    }
    var appsInfo by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(selectedPackages) {
        withContext(Dispatchers.IO) {
            appsInfo =
                selectedPackages
                    .mapNotNull { pkg ->
                        try {
                            val info = packageManager.getApplicationInfo(pkg, 0)
                            AppInfo(
                                pkg,
                                info.loadLabel(packageManager).toString(),
                                info.loadIcon(packageManager),
                            )
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                    .sortedBy { it.label.lowercase() }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceBright)
                .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(
            visible = appsInfo.isNotEmpty(),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                appsInfo.forEach { app ->
                    InputChip(
                        selected = true,
                        onClick = {
                            val newPackages = selectedPackages - app.packageName
                            flow.putString(settingKey, newPackages.joinToString(","))
                        },
                        label = { Text(app.label) },
                        leadingIcon = {
                            app.icon?.let { icon ->
                                Image(
                                    bitmap = icon.toBitmap(24, 24).asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).clip(CircleShape),
                                )
                            }
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors =
                            InputChipDefaults.inputChipColors(
                                selectedContainerColor =
                                    MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedLeadingIconColor =
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTrailingIconColor =
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        border =
                            InputChipDefaults.inputChipBorder(
                                enabled = true,
                                selected = true,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                    )
                }
            }
        }

        if (appsInfo.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AppListAddButton(text = addButtonText, onClick = { onAddClicked(selectedPackages) })
    }
}

@Composable
private fun AppListAddButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "scale",
        )

    Row(
        modifier =
            Modifier.scale(scale)
                .clip(ExpressiveShapes.large)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        Text(
            text = text,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
