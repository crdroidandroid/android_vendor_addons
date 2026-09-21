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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.axion.compose.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MainSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentAlpha = if (enabled) 1f else 0.38f
    val hasSummary = !summary.isNullOrEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PreferenceTokens.MinHeight)
                .clip(RoundedCornerShape(PreferenceTokens.CornerRadiusPill))
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                ) { onCheckedChange(!checked) }
                .padding(
                    start = PreferenceTokens.MainSwitchPaddingStart,
                    end = PreferenceTokens.MainSwitchPaddingEnd,
                    top = PreferenceTokens.MainSwitchPaddingVertical,
                    bottom = PreferenceTokens.MainSwitchPaddingVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasSummary) {
                    Text(
                        text = summary!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(PreferenceTokens.WidgetSpacing))

            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = null,
                interactionSource = interactionSource,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun SettingMainSwitch(
    settingKey: String,
    title: String,
    type: SettingsType = SettingsType.SECURE,
    summary: String? = null,
    defaultValue: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val flow = rememberSettingsFlow(type)
    val isChecked by rememberSettingBoolean(settingKey, type, defaultValue)

    MainSwitchPreference(
        title = title,
        summary = summary,
        checked = isChecked,
        onCheckedChange = { flow.putInt(settingKey, if (it) 1 else 0) },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun SecureSettingMainSwitch(
    settingKey: String,
    title: String,
    summary: String? = null,
    defaultValue: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingMainSwitch(settingKey, title, SettingsType.SECURE, summary, defaultValue, modifier, enabled)
}

@Composable
fun SystemSettingMainSwitch(
    settingKey: String,
    title: String,
    summary: String? = null,
    defaultValue: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SettingMainSwitch(settingKey, title, SettingsType.SYSTEM, summary, defaultValue, modifier, enabled)
}
