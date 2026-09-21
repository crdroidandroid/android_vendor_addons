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

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    iconDrawable: Drawable? = null,
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
        iconDrawable = iconDrawable,
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
    iconDrawable: Drawable? = null,
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
        iconDrawable = iconDrawable,
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
    iconDrawable: Drawable? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current,
) {
    SettingSwitch(settingKey, title, SettingsType.SECURE, summary, defaultValue,
        modifier, icon, customIcon, iconDrawable, enabled, iconTint, iconBackgroundColor, position)
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
    iconDrawable: Drawable? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current,
) {
    SettingSwitch(settingKey, title, SettingsType.SYSTEM, summary, defaultValue,
        modifier, icon, customIcon, iconDrawable, enabled, iconTint, iconBackgroundColor, position)
}
