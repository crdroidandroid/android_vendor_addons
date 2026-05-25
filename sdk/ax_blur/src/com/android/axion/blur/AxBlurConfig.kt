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

import android.content.Context
import android.content.res.Resources
import android.os.UserHandle
import android.provider.Settings
import com.android.axion.kotlin.math.scaleRatioLocked
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.math.roundToInt

object AxBlurConfig {
    const val BASE_MAX_BLUR_RADIUS_PX = 120f
    const val BASE_SCALE_DP = 420f
    const val BASE_WIDTH_PX = 1080f

    const val KEY_SYSTEM_BLUR_RADIUS_PCT = "system_blur_radius_pct"
    const val MIN_BLUR_RADIUS_PCT = 0f
    const val MAX_BLUR_RADIUS_PCT = 100f
    const val DEFAULT_BLUR_RADIUS_PCT = MAX_BLUR_RADIUS_PCT

    const val KEY_LAUNCHER_BLUR_ENABLED = "pulse_launcher_blur_enabled"
    const val KEY_LAUNCHER_BLUR_RADIUS_PCT = "pulse_launcher_blur_radius_pct"
    const val DEFAULT_LAUNCHER_BLUR_ENABLED = true
    private const val DEFAULT_LAUNCHER_BLUR_ENABLED_INT = 1
    const val MIN_LAUNCHER_BLUR_RADIUS_PCT = 0
    const val MAX_LAUNCHER_BLUR_RADIUS_PCT = 100
    const val DEFAULT_LAUNCHER_BLUR_RADIUS_PCT = MAX_LAUNCHER_BLUR_RADIUS_PCT

    @JvmStatic
    fun getScaleRatio(context: Context): Float = context.scaleRatioLocked

    @JvmStatic
    fun getScaleRatio(resources: Resources): Float = resources.scaleRatioLocked

    @JvmStatic
    @JvmOverloads
    fun getMaxBlurRadiusPx(
        context: Context,
        baseRadiusPx: Float = BASE_MAX_BLUR_RADIUS_PX,
    ): Float = getMaxBlurRadiusPx(context.resources, baseRadiusPx)

    @JvmStatic
    @JvmOverloads
    fun getMaxBlurRadiusPx(
        resources: Resources,
        baseRadiusPx: Float = BASE_MAX_BLUR_RADIUS_PX,
    ): Float {
        val dm = resources.displayMetrics
        val scaleRatio = resources.scaleRatioLocked
        val baseDp = baseRadiusPx * BASE_SCALE_DP / BASE_WIDTH_PX
        return baseDp * dm.density * scaleRatio
    }

    @JvmStatic
    @JvmOverloads
    fun getSystemBlurRadiusPx(
        context: Context,
        baseRadiusPx: Float = BASE_MAX_BLUR_RADIUS_PX,
    ): Float {
        val maxRadius = getMaxBlurRadiusPx(context, baseRadiusPx)
        val percent = getSecureFloat(context, KEY_SYSTEM_BLUR_RADIUS_PCT, DEFAULT_BLUR_RADIUS_PCT)
        if (!percent.isFinite()) return maxRadius
        return maxRadius * percent.coerceIn(MIN_BLUR_RADIUS_PCT, MAX_BLUR_RADIUS_PCT) / MAX_BLUR_RADIUS_PCT
    }

    @JvmStatic
    fun isLauncherBlurEnabled(context: Context): Boolean {
        return getSecureInt(context, KEY_LAUNCHER_BLUR_ENABLED, DEFAULT_LAUNCHER_BLUR_ENABLED_INT) != 0
    }

    @JvmStatic
    fun getLauncherBlurRadiusPct(context: Context): Int {
        val raw = getSecureInt(context, KEY_LAUNCHER_BLUR_RADIUS_PCT, DEFAULT_LAUNCHER_BLUR_RADIUS_PCT)
        return raw.coerceIn(MIN_LAUNCHER_BLUR_RADIUS_PCT, MAX_LAUNCHER_BLUR_RADIUS_PCT)
    }

    @JvmStatic
    fun getLauncherBlurRadiusPx(context: Context): Int {
        if (!isLauncherBlurEnabled(context)) return 0
        val maxRadius = getMaxBlurRadiusPx(context)
        val pct = getLauncherBlurRadiusPct(context)
        return (maxRadius * pct / MAX_LAUNCHER_BLUR_RADIUS_PCT.toFloat()).roundToInt()
    }

    private fun getSecureFloat(context: Context, key: String, def: Float): Float = try {
        Settings.Secure.getFloatForUser(context.contentResolver, key, def, UserHandle.myUserId())
    } catch (_: Exception) {
        def
    }

    private fun getSecureInt(context: Context, key: String, def: Int): Int = try {
        Settings.Secure.getIntForUser(context.contentResolver, key, def, UserHandle.myUserId())
    } catch (_: Exception) {
        def
    }
}
