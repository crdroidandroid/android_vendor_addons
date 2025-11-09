/*
 * Copyright (C) 2025 AxionOS Project
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class PreferencePosition {
    Single,
    Top,
    Middle,
    Bottom
}

private val preferenceCornerRadius = 28.dp
private val bottomTopCornerRadius = 4.dp

val LocalPreferencePosition = compositionLocalOf { PreferencePosition.Single }

fun preferenceShape(position: PreferencePosition): Shape {
    return when (position) {
        PreferencePosition.Single -> RoundedCornerShape(preferenceCornerRadius)
        PreferencePosition.Top -> RoundedCornerShape(
            topStart = preferenceCornerRadius,
            topEnd = preferenceCornerRadius,
            bottomStart = bottomTopCornerRadius,
            bottomEnd = bottomTopCornerRadius
        )
        PreferencePosition.Middle -> RoundedCornerShape(4.dp)
        PreferencePosition.Bottom -> RoundedCornerShape(
            topStart = bottomTopCornerRadius,
            topEnd = bottomTopCornerRadius,
            bottomStart = preferenceCornerRadius,
            bottomEnd = preferenceCornerRadius
        )
    }
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current
) {
    val interactionSource = remember { MutableInteractionSource() }

    BasePreference(
        title = title,
        summary = summary,
        icon = icon,
        customIcon = customIcon,
        enabled = enabled,
        iconTint = iconTint,
        iconBackgroundColor = iconBackgroundColor,
        position = position,
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
        ) { onCheckedChange(!checked) },
        widget = {
            Spacer(modifier = Modifier.width(16.dp))
            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = null,
                interactionSource = interactionSource,
                enabled = enabled,
            )
        },
    )
}

@Composable
fun SettingSwitch(
    settingKey: String,
    title: String,
    type: SettingsType = SettingsType.SECURE,
    summary: String? = null,
    defaultValue: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current,
) {
    val flow = rememberSettingsFlow(type)
    val isChecked by rememberSettingBoolean(settingKey, type, defaultValue)

    SwitchPreference(
        title = title,
        summary = summary,
        checked = isChecked,
        onCheckedChange = { flow.putInt(settingKey, if (it) 1 else 0) },
        modifier = modifier,
        icon = icon,
        customIcon = customIcon,
        enabled = enabled,
        iconTint = iconTint,
        iconBackgroundColor = iconBackgroundColor,
        position = position,
    )
}

@Composable
fun SecureSettingSwitch(
    settingKey: String,
    title: String,
    summary: String? = null,
    defaultValue: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current,
) {
    SettingSwitch(settingKey, title, SettingsType.SECURE, summary, defaultValue,
        modifier, icon, customIcon, enabled, iconTint, iconBackgroundColor, position)
}

@Composable
fun SystemSettingSwitch(
    settingKey: String,
    title: String,
    summary: String? = null,
    defaultValue: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current,
) {
    SettingSwitch(settingKey, title, SettingsType.SYSTEM, summary, defaultValue,
        modifier, icon, customIcon, enabled, iconTint, iconBackgroundColor, position)
}
