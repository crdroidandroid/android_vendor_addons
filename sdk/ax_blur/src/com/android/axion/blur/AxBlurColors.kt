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
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View

object AxBlurColors {
    @JvmStatic
    fun tint(context: Context): Int {
        return surfaceEffect0(context)
    }

    @JvmStatic
    fun fallback(context: Context): Int {
        return color(context, R.color.ax_blur_fallback)
    }

    @JvmStatic
    fun surfaceEffect(context: Context, level: Int): Int {
        return color(context, surfaceEffectResource(level))
    }

    @JvmStatic
    fun surfaceEffect0(context: Context): Int {
        return surfaceEffect(context, 0)
    }

    @JvmStatic
    fun surfaceEffect1(context: Context): Int {
        return surfaceEffect(context, 1)
    }

    @JvmStatic
    fun surfaceEffect2(context: Context): Int {
        return surfaceEffect(context, 2)
    }

    @JvmStatic
    fun surfaceEffect3(context: Context): Int {
        return surfaceEffect(context, 3)
    }

    @JvmStatic
    fun surfaceBright(context: Context): Int {
        return color(context, R.color.ax_blur_surface_bright_expressive)
    }

    @JvmStatic
    fun surfaceBrightTint(context: Context): Int {
        return color(context, R.color.ax_blur_surface_bright_expressive_tint)
    }

    @JvmStatic
    fun surfaceBrightTintList(context: Context): ColorStateList {
        return colorStateList(context, R.color.ax_blur_surface_bright_expressive_tint)
    }

    @JvmStatic
    fun surfaceContainer(context: Context): Int {
        return color(context, R.color.ax_blur_surface_container_expressive)
    }

    @JvmStatic
    fun surfaceContainerTint(context: Context): Int {
        return color(context, R.color.ax_blur_surface_container_expressive_tint)
    }

    @JvmStatic
    fun surfaceContainerTintList(context: Context): ColorStateList {
        return colorStateList(context, R.color.ax_blur_surface_container_expressive_tint)
    }

    @JvmStatic
    @JvmOverloads
    fun applySurfaceEffect(view: View?, level: Int = 0) {
        if (view == null) return
        view.backgroundTintList = null
        val background = view.background?.mutate()
        val color = surfaceEffect(view.context, level)
        if (background is GradientDrawable) {
            background.setColor(color)
            view.background = background
        } else {
            view.setBackgroundColor(color)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun applySurfaceEffect(drawable: GradientDrawable?, context: Context, level: Int = 0) {
        drawable?.setColor(surfaceEffect(context, level))
    }

    @JvmStatic
    @JvmOverloads
    fun surfaceDrawable(context: Context, level: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(surfaceEffect(context, level))
            cornerRadius = dialogCornerRadius(context)
        }
    }

    private fun surfaceEffectResource(level: Int): Int {
        return when (level) {
            0 -> R.color.ax_blur_surface_effect_0
            2 -> R.color.ax_blur_surface_effect_2
            3 -> R.color.ax_blur_surface_effect_3
            else -> R.color.ax_blur_surface_effect_1
        }
    }

    private fun color(context: Context, resId: Int): Int {
        return context.resources.getColor(resId, context.theme)
    }

    private fun colorStateList(context: Context, resId: Int): ColorStateList {
        return context.resources.getColorStateList(resId, context.theme)
    }

    private fun dialogCornerRadius(context: Context): Float {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.dialogCornerRadius, value, true)) {
            return 0f
        }
        if (value.type != TypedValue.TYPE_DIMENSION) {
            return 0f
        }
        return TypedValue.complexToDimension(value.data, context.resources.displayMetrics)
    }
}
