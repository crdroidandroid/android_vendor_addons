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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PreferencePosition {
    Single,
    Top,
    Middle,
    Bottom
}

val LocalPreferencePosition = compositionLocalOf { PreferencePosition.Single }

object PreferenceTokens {
    val MinHeight: Dp = 72.dp
    val PaddingHorizontal: Dp = 16.dp
    val PaddingVertical: Dp = 12.dp

    val CornerRadiusOuter: Dp = 20.dp
    val CornerRadiusInner: Dp = 4.dp
    val CornerRadiusPill: Dp = 42.dp

    val IconFrameSize: Dp = 40.dp
    val IconSize: Dp = 24.dp
    val IconSpacing: Dp = 12.dp

    val WidgetSpacing: Dp = 16.dp
    val WidgetFrameMinWidth: Dp = 48.dp

    val DividerHeight: Dp = 40.dp
    val DividerWidth: Dp = 1.dp
    val DividerTrailingSpacing: Dp = 12.dp

    val SliderHorizontalPadding: Dp = 16.dp
    val SliderBottomSpacing: Dp = 8.dp

    val MainSwitchPaddingStart: Dp = 32.dp
    val MainSwitchPaddingEnd: Dp = 20.dp
    val MainSwitchPaddingVertical: Dp = 16.dp

    val CardPaddingEnd: Dp = 12.dp

    val CategoryPaddingStart: Dp = 16.dp
    val CategoryPaddingTop: Dp = 20.dp
    val CategoryPaddingBottom: Dp = 8.dp

    val DialogItemPaddingHorizontal: Dp = 24.dp
    val DialogItemPaddingVertical: Dp = 12.dp

    val DraggableStep: Dp = 73.dp
}

fun preferencePosition(
    index: Int,
    count: Int,
    firstIsMiddle: Boolean = false,
): PreferencePosition {
    require(count > 0) { "Preference count must be positive" }
    require(index in 0 until count) { "Preference index must be within the preference count" }
    return when {
        count == 1 -> PreferencePosition.Single
        index == 0 -> if (firstIsMiddle) PreferencePosition.Middle else PreferencePosition.Top
        index == count - 1 -> PreferencePosition.Bottom
        else -> PreferencePosition.Middle
    }
}

fun preferenceShape(position: PreferencePosition): Shape {
    return when (position) {
        PreferencePosition.Single -> RoundedCornerShape(PreferenceTokens.CornerRadiusOuter)
        PreferencePosition.Top -> RoundedCornerShape(
            topStart = PreferenceTokens.CornerRadiusOuter,
            topEnd = PreferenceTokens.CornerRadiusOuter,
            bottomStart = PreferenceTokens.CornerRadiusInner,
            bottomEnd = PreferenceTokens.CornerRadiusInner,
        )
        PreferencePosition.Middle -> RoundedCornerShape(PreferenceTokens.CornerRadiusInner)
        PreferencePosition.Bottom -> RoundedCornerShape(
            topStart = PreferenceTokens.CornerRadiusInner,
            topEnd = PreferenceTokens.CornerRadiusInner,
            bottomStart = PreferenceTokens.CornerRadiusOuter,
            bottomEnd = PreferenceTokens.CornerRadiusOuter,
        )
    }
}
