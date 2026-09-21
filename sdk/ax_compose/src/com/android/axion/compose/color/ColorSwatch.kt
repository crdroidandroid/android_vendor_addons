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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

@Composable
fun AxSplitColorSwatch(
    label: String,
    lightColor: Color,
    darkColor: Color,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentInset: Dp = 4.dp,
) {
    AxColorSwatchFrame(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        size = size,
    ) {
        val contentSize = (size - contentInset * 2).coerceAtLeast(0.dp)
        Row(
            modifier = Modifier
                .size(contentSize)
                .clip(CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .size(width = contentSize / 2, height = contentSize)
                    .background(lightColor),
            )
            Box(
                modifier = Modifier
                    .size(width = contentSize / 2, height = contentSize)
                    .background(darkColor),
            )
        }
    }
}

@Composable
fun AxColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentInset: Dp = 4.dp,
) {
    AxColorSwatchFrame(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        size = size,
    ) {
        val contentSize = (size - contentInset * 2).coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .size(contentSize)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
fun AxCustomColorSwatch(
    color: Color?,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentInset: Dp = 4.dp,
) {
    AxColorSwatchFrame(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        size = size,
    ) {
        val contentSize = (size - contentInset * 2).coerceAtLeast(0.dp)
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(contentSize)
                    .clip(CircleShape)
                    .background(color),
            )
        } else {
            AxRainbowSwatch(size = contentSize)
        }
    }
}

@Composable
fun AxRainbowSwatch(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val colors = listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
        )
        val sweep = 360f / colors.size
        colors.forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = index * sweep - 90f,
                sweepAngle = sweep + 1f,
                useCenter = true,
            )
        }
    }
}

@Composable
private fun AxColorSwatchFrame(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    size: Dp,
    content: @Composable () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
