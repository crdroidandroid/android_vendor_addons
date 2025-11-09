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

package com.android.axion.compose.preferences

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.res.stringResource
import com.android.axion.compose.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current,
    onReset: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = preferenceShape(position)
    val haptic = LocalHapticFeedback.current
    val contentAlpha = if (enabled) 1f else 0.38f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .alpha(contentAlpha)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 60.dp)
                )

                if (onReset != null && enabled) {
                    val resetHint = stringResource(R.string.long_press_to_reset)
                    val toastContext = LocalContext.current
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
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
                .padding(horizontal = 24.dp)
                .alpha(contentAlpha),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
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
        enabled = enabled,
        position = position
    )
}
