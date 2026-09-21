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

package com.android.axion.compose.color

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.axion.compose.R

private val DialogMaxWidth = 360.dp
private val PreviewHeight = 72.dp
private val SliderHeight = 44.dp
private val PresetSwatchSize = 24.dp

data class ColorPickerPreset(
    val label: String,
    val color: Color,
)

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    presets: List<ColorPickerPreset> = emptyList(),
    defaultsLabel: String? = null,
    hueLabel: String? = null,
    saturationLabel: String? = null,
    brightnessLabel: String? = null,
    hexLabel: String? = null,
    cancelLabel: String? = null,
    applyLabel: String? = null,
) {
    val resolvedTitle = title ?: stringResource(R.string.ax_color_picker_title)
    val resolvedDefaultsLabel = defaultsLabel ?: stringResource(R.string.ax_color_picker_defaults)
    val resolvedHueLabel = hueLabel ?: stringResource(R.string.ax_color_picker_hue)
    val resolvedSaturationLabel = saturationLabel
        ?: stringResource(R.string.ax_color_picker_saturation)
    val resolvedBrightnessLabel = brightnessLabel
        ?: stringResource(R.string.ax_color_picker_brightness)
    val resolvedHexLabel = hexLabel ?: stringResource(R.string.ax_color_picker_hex)
    val resolvedCancelLabel = cancelLabel ?: stringResource(R.string.ax_color_picker_cancel)
    val resolvedApplyLabel = applyLabel ?: stringResource(R.string.ax_color_picker_apply)
    val colors = MaterialTheme.colorScheme

    var selectedColor by remember { mutableStateOf(initialColor) }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0.5f) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var hexField by remember { mutableStateOf(TextFieldValue(initialColor.toHexString())) }

    fun syncFromColor(color: Color, updateHex: Boolean) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        selectedColor = Color.hsv(hue, saturation, brightness)
        if (updateHex) {
            val hex = selectedColor.toHexString()
            hexField = TextFieldValue(hex, TextRange(hex.length))
        }
    }

    fun syncFromHsv() {
        selectedColor = Color.hsv(hue, saturation, brightness)
        val hex = selectedColor.toHexString()
        hexField = TextFieldValue(hex, TextRange(hex.length))
    }

    LaunchedEffect(initialColor) {
        syncFromColor(initialColor, updateHex = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(0.9f).widthIn(max = DialogMaxWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PreviewHeight)
                        .clip(MaterialTheme.shapes.large)
                        .background(selectedColor)
                        .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.large),
                )

                if (presets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = resolvedDefaultsLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { preset ->
                            ColorPresetChip(
                                preset = preset,
                                selected = preset.color.toArgb() == selectedColor.toArgb(),
                                onClick = { syncFromColor(preset.color, updateHex = true) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                ColorSliderLabel(resolvedHueLabel)
                HuePicker(
                    hue = hue,
                    onHueChange = { value ->
                        hue = value
                        syncFromHsv()
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                ColorSliderLabel(resolvedSaturationLabel)
                ColorComponentSlider(
                    value = saturation,
                    onValueChange = { value ->
                        saturation = value
                        syncFromHsv()
                    },
                    startColor = Color.hsv(hue, 0f, brightness),
                    endColor = Color.hsv(hue, 1f, brightness),
                )

                Spacer(modifier = Modifier.height(16.dp))

                ColorSliderLabel(resolvedBrightnessLabel)
                ColorComponentSlider(
                    value = brightness,
                    onValueChange = { value ->
                        brightness = value
                        syncFromHsv()
                    },
                    startColor = Color.hsv(hue, saturation, 0f),
                    endColor = Color.hsv(hue, saturation, 1f),
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = hexField,
                    onValueChange = { value ->
                        val filtered = value.text.uppercase()
                            .filter { it in "0123456789ABCDEF" }
                            .take(6)
                        hexField = TextFieldValue(
                            filtered,
                            TextRange(filtered.length.coerceAtMost(value.selection.start)),
                        )
                        if (filtered.length == 6) {
                            try {
                                syncFromColor(
                                    Color(AndroidColor.parseColor("#$filtered")),
                                    updateHex = false,
                                )
                            } catch (_: IllegalArgumentException) {
                            }
                        }
                    },
                    label = { Text(resolvedHexLabel) },
                    prefix = { Text("#") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(resolvedCancelLabel)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(selectedColor) }) {
                        Text(resolvedApplyLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPresetChip(
    preset: ColorPickerPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AxColorSwatch(
                color = preset.color,
                selected = false,
                size = PresetSwatchSize,
                contentInset = 0.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ColorSliderLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun HuePicker(hue: Float, onHueChange: (Float) -> Unit) {
    val selectorColor = MaterialTheme.colorScheme.surface
    val selectorOutlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onHueChange((offset.x / size.width * 360f).coerceIn(0f, 360f))
                        },
                        onDrag = { change, _ ->
                            onHueChange(
                                (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                            )
                            change.consume()
                        },
                    )
                },
        ) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color.hsv(0f, 1f, 1f),
                        Color.hsv(60f, 1f, 1f),
                        Color.hsv(120f, 1f, 1f),
                        Color.hsv(180f, 1f, 1f),
                        Color.hsv(240f, 1f, 1f),
                        Color.hsv(300f, 1f, 1f),
                        Color.hsv(360f, 1f, 1f),
                    )
                )
            )
            drawSliderSelector(
                center = Offset((hue / 360f) * size.width, size.height / 2f),
                fillColor = selectorColor,
                outlineColor = selectorOutlineColor,
            )
        }
    }
}

@Composable
private fun ColorComponentSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    startColor: Color,
    endColor: Color,
) {
    val selectorColor = MaterialTheme.colorScheme.surface
    val selectorOutlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                        },
                        onDrag = { change, _ ->
                            onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                            change.consume()
                        },
                    )
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(startColor, endColor)))
            drawSliderSelector(
                center = Offset(value * size.width, size.height / 2f),
                fillColor = selectorColor,
                outlineColor = selectorOutlineColor,
            )
        }
    }
}

private fun DrawScope.drawSliderSelector(
    center: Offset,
    fillColor: Color,
    outlineColor: Color,
) {
    val selectorCenter = Offset(center.x.coerceIn(0f, size.width), center.y)
    drawCircle(
        color = fillColor,
        radius = 11.dp.toPx(),
        center = selectorCenter,
    )
    drawCircle(
        color = outlineColor,
        radius = 11.dp.toPx(),
        center = selectorCenter,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun Color.toHexString(): String = String.format("%06X", 0xFFFFFF and toArgb())
