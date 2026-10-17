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

package com.android.axion.blur

/**
 * Opacity of the frost tint over window-surface blur (0x8A ≈ 54 %). Keep
 * `config_axBlurSurfaceAlpha` (res/values/config.xml) equal to this / 255 so View and Compose
 * surfaces frost to the same density.
 */
internal const val AX_BLUR_SURFACE_ALPHA = 0x8A

/**
 * Saturation applied to the in-window ("source") blur. A blur averages neighbouring pixels and
 * reads greyer than what's behind it; lifting saturation keeps the colour of the backdrop showing
 * through, which is most of what makes iOS materials look like glass rather than frosted plastic.
 * The cross-window blur is composited by SurfaceFlinger and can't take a colour filter.
 */
internal const val AX_BLUR_SOURCE_SATURATION = 1.5f
