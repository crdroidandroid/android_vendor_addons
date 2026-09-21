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

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.axion.compose.preferences

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.axion.compose.R
import kotlin.math.roundToInt

@Composable
fun SliderPreference(
    title: String,
    summary: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    displayValue: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    iconDrawable: Drawable? = null,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current,
    onReset: (() -> Unit)? = null,
    onValueClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = preferenceShape(position)
    val haptic = LocalHapticFeedback.current
    val contentAlpha = if (enabled) 1f else 0.38f
    val hasSummary = summary.isNotEmpty()
    val resolvedIconTint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PreferenceTokens.MinHeight)
                .alpha(contentAlpha)
                .padding(
                    start = PreferenceTokens.PaddingHorizontal,
                    end = PreferenceTokens.PaddingHorizontal,
                    top = PreferenceTokens.PaddingVertical,
                    bottom = if (hasSummary) 0.dp else 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (customIcon != null) {
                Box(
                    modifier = Modifier
                        .size(PreferenceTokens.IconFrameSize)
                        .alpha(contentAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    customIcon()
                }
                Spacer(modifier = Modifier.width(PreferenceTokens.IconSpacing))
            } else if (iconDrawable != null) {
                Box(
                    modifier = Modifier
                        .size(PreferenceTokens.IconFrameSize)
                        .clip(CircleShape)
                        .then(
                            if (iconBackgroundColor != null) Modifier.background(iconBackgroundColor)
                            else Modifier
                        )
                        .alpha(contentAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = remember(iconDrawable) {
                        val width = if (iconDrawable.intrinsicWidth > 0) iconDrawable.intrinsicWidth else 48
                        val height = if (iconDrawable.intrinsicHeight > 0) iconDrawable.intrinsicHeight else 48
                        iconDrawable.toBitmap(width, height).asImageBitmap()
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(PreferenceTokens.IconSize),
                    )
                }
                Spacer(modifier = Modifier.width(PreferenceTokens.IconSpacing))
            } else if (icon != null) {
                PreferenceIcon(
                    icon = icon,
                    tint = resolvedIconTint,
                    backgroundColor = iconBackgroundColor,
                    contentAlpha = contentAlpha,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasSummary) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .widthIn(min = 60.dp)
                        .then(
                            if (onValueClick != null && enabled) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onValueClick,
                                )
                            } else {
                                Modifier
                            }
                        )
                )

                if (onReset != null && enabled) {
                    val resetHint = stringResource(R.string.long_press_to_reset)
                    val toastContext = LocalContext.current
                    Spacer(modifier = Modifier.width(PreferenceTokens.PaddingHorizontal))
                    Box(
                        modifier = Modifier
                            .size(PreferenceTokens.IconSize)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {
                                    Toast.makeText(toastContext, resetHint, Toast.LENGTH_SHORT).show()
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onReset()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PreferenceTokens.SliderHorizontalPadding)
                .alpha(contentAlpha),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )

        Spacer(modifier = Modifier.height(PreferenceTokens.SliderBottomSpacing))
    }
}

@Composable
fun SecureSettingSlider(
    settingKey: String,
    title: String,
    summary: String,
    min: Int = 0,
    max: Int,
    interval: Int = 1,
    unit: String = "",
    defaultValue: Int = min,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    iconDrawable: Drawable? = null,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current,
    formatValue: ((Int) -> String)? = null
) {
    SettingsSliderBase(
        settingsType = SettingsType.SECURE,
        settingKey = settingKey,
        title = title,
        summary = summary,
        min = min,
        max = max,
        interval = interval,
        unit = unit,
        defaultValue = defaultValue,
        modifier = modifier,
        icon = icon,
        customIcon = customIcon,
        iconDrawable = iconDrawable,
        iconTint = iconTint,
        iconBackgroundColor = iconBackgroundColor,
        enabled = enabled,
        position = position,
        formatValue = formatValue
    )
}

@Composable
fun SystemSettingSlider(
    settingKey: String,
    title: String,
    summary: String,
    min: Int = 0,
    max: Int,
    interval: Int = 1,
    unit: String = "",
    defaultValue: Int = min,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    iconDrawable: Drawable? = null,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current,
    formatValue: ((Int) -> String)? = null
) {
    SettingsSliderBase(
        settingsType = SettingsType.SYSTEM,
        settingKey = settingKey,
        title = title,
        summary = summary,
        min = min,
        max = max,
        interval = interval,
        unit = unit,
        defaultValue = defaultValue,
        modifier = modifier,
        icon = icon,
        customIcon = customIcon,
        iconDrawable = iconDrawable,
        iconTint = iconTint,
        iconBackgroundColor = iconBackgroundColor,
        enabled = enabled,
        position = position,
        formatValue = formatValue
    )
}

@Composable
fun SettingsSliderBase(
    settingsType: SettingsType,
    settingKey: String,
    title: String,
    summary: String,
    min: Int = 0,
    max: Int,
    interval: Int = 1,
    unit: String = "",
    defaultValue: Int = min,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    customIcon: @Composable (() -> Unit)? = null,
    iconDrawable: Drawable? = null,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current,
    formatValue: ((Int) -> String)? = null
) {
    val flow = rememberSettingsFlow(settingsType)
    val observed by rememberSettingInt(settingKey, settingsType, defaultValue)
    var currentValue by remember { mutableFloatStateOf(flow.getInt(settingKey, defaultValue).toFloat()) }

    LaunchedEffect(observed) {
        currentValue = observed.toFloat()
    }
    
    val displayValue = formatValue?.invoke(currentValue.roundToInt()) ?: run {
        val intValue = currentValue.roundToInt()
        when {
            unit.equals("MHz", ignoreCase = true) -> "${intValue / 1000} MHz"
            unit.equals("Level", ignoreCase = true) -> "Level $intValue"
            unit.isNotEmpty() -> "$intValue $unit"
            else -> intValue.toString()
        }
    }
    
    SliderPreference(
        title = title,
        summary = summary,
        value = currentValue,
        onValueChange = { newValue ->
            val steppedValue = ((newValue - min) / interval).roundToInt() * interval + min
            currentValue = steppedValue.coerceIn(min, max).toFloat()
        },
        onValueChangeFinished = {
            flow.putInt(settingKey, currentValue.roundToInt())
        },
        onReset = {
            flow.putInt(settingKey, defaultValue)
            currentValue = defaultValue.toFloat()
        },
        valueRange = min.toFloat()..max.toFloat(),
        steps = 0, 
        displayValue = displayValue,
        modifier = modifier,
        icon = icon,
        customIcon = customIcon,
        iconDrawable = iconDrawable,
        iconTint = iconTint,
        iconBackgroundColor = iconBackgroundColor,
        enabled = enabled,
        position = position
    )
}
