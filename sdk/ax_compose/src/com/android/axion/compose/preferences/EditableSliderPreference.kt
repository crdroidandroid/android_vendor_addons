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

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun EditableSliderPreference(
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
    SliderPreference(
        title = title,
        summary = summary,
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        displayValue = displayValue,
        modifier = modifier,
        icon = icon,
        customIcon = customIcon,
        iconDrawable = iconDrawable,
        iconTint = iconTint,
        iconBackgroundColor = iconBackgroundColor,
        enabled = enabled,
        position = position,
        onReset = onReset,
        onValueClick = onValueClick,
    )
}
