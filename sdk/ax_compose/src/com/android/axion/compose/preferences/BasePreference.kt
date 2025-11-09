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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun BasePreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current,
    enlargeTitle: Boolean = false,
    widget: @Composable (() -> Unit)? = null,
) {
    val shape = preferenceShape(position)
    val contentAlpha = if (enabled) 1f else 0.38f
    val hasSummary = summary != null
    val resolvedIconTint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (hasSummary) 72.dp else 60.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .then(modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (customIcon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .alpha(contentAlpha),
                contentAlignment = Alignment.Center,
            ) {
                customIcon()
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            PreferenceIcon(
                icon = icon,
                tint = resolvedIconTint,
                backgroundColor = iconBackgroundColor,
                contentAlpha = contentAlpha,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha),
        ) {
            Text(
                text = title,
                style = if (hasSummary || !enlargeTitle) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = if (hasSummary) Modifier.padding(vertical = 2.dp) else Modifier,
            )
            if (hasSummary) {
                Text(
                    text = summary!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (widget != null) {
            widget()
        }
    }
}

@Composable
fun PreferenceIcon(
    icon: ImageVector?,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    backgroundColor: Color? = null,
    contentAlpha: Float = 1f,
) {
    if (icon != null) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (backgroundColor != null) Modifier.background(backgroundColor)
                    else Modifier
                )
                .alpha(contentAlpha),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
    } else {
        Spacer(modifier = Modifier.width(16.dp))
    }
}
