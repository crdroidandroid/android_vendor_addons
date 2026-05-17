/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.crdroid.utils

import android.content.Context
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.util.TypedValue

import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen

import com.google.android.material.R

import kotlin.math.abs
import kotlin.random.Random

object IconTinterUtils {

    private const val ICON_STYLE = "settings_icon_style"
    private const val ICON_RANDOM_COLORS = "settings_icon_random_colors"
    private const val ICON_CORNER_STYLE = "settings_icon_corner_style"

    private const val ICON_STYLE_MATERIAL_EXPRESSIVE_ICON = 0
    private const val ICON_STYLE_SOLID_BG_WHITE_ICON = 1
    private const val ICON_STYLE_GRADIENT_BG_WHITE_ICON = 2
    private const val ICON_STYLE_ACCENT_OUTLINE_ACCENT_ICON = 3
    private const val ICON_STYLE_SOLID_OUTLINE_SOLID_ICON = 4
    private const val ICON_STYLE_COLOR_ICON_NO_BG = 5
    private const val ICON_STYLE_ACCENT_ICON = 6

    private const val CORNER_STYLE_ROUND = 0
    private const val CORNER_STYLE_SQUARISH = 1

    private val MATERIAL_COLOR_BG_RES_IDS = intArrayOf(
        R.color.m3_ref_palette_blue90,
        R.color.m3_ref_palette_pink90,
        R.color.m3_ref_palette_orange90,
        R.color.m3_ref_palette_yellow90,
        R.color.m3_ref_palette_blue_variant90,
        R.color.m3_ref_palette_green90,
        R.color.m3_ref_palette_grey90,
        R.color.m3_ref_palette_cyan90,
        R.color.m3_ref_palette_red90,
        R.color.m3_ref_palette_purple90
    )

    private val MATERIAL_COLOR_FG_RES_IDS = intArrayOf(
        R.color.m3_ref_palette_blue30,
        R.color.m3_ref_palette_pink30,
        R.color.m3_ref_palette_orange30,
        R.color.m3_ref_palette_yellow30,
        R.color.m3_ref_palette_blue_variant30,
        R.color.m3_ref_palette_green30,
        R.color.m3_ref_palette_grey30,
        R.color.m3_ref_palette_cyan30,
        R.color.m3_ref_palette_red30,
        R.color.m3_ref_palette_purple30
    )

    private const val ROUND_CORNER_RADIUS_DP = 99
    private const val SQUARISH_CORNER_RADIUS_DP = 15
    private const val BG_PADDING_DP = 8
    private const val OUTLINE_WIDTH_DP = 2
    private const val BG_ALPHA = 0.4f
    private const val ICON_SATURATION_BOOST = 0.3f
    private const val SOLID_BG_SATURATION_BOOST = 0.5f
    private const val GRADIENT_COLOR_FACTOR = 0.5f
    private const val GRADIENT_SATURATION_FACTOR = 0.5f

    private const val ICON_LAYER_ID = 0x7F1C0001

    private data class IconColors(@ColorInt val bg: Int, @ColorInt val fg: Int)

    private val colorCache = HashMap<String, IconColors>()

    @JvmStatic
    fun tintIcons(screen: PreferenceScreen?, context: Context?) {
        if (screen == null || context == null) return

        val iconStyle = Settings.System.getIntForUser(
            context.contentResolver,
            ICON_STYLE,
            ICON_STYLE_MATERIAL_EXPRESSIVE_ICON,
            UserHandle.USER_CURRENT
        )

        val randomColors = Settings.System.getIntForUser(
            context.contentResolver,
            ICON_RANDOM_COLORS,
            0,
            UserHandle.USER_CURRENT
        ) == 1

        val cornerStyle = Settings.System.getIntForUser(
            context.contentResolver,
            ICON_CORNER_STYLE,
            CORNER_STYLE_ROUND,
            UserHandle.USER_CURRENT
        )

        val res = context.resources

        if (randomColors) {
            colorCache.clear()
        }

        for (i in 0 until screen.preferenceCount) {
            val preference = screen.getPreference(i)
            tintPreferenceIcon(preference, context, res, iconStyle, randomColors, cornerStyle)

            if (preference is PreferenceGroup) {
                for (j in 0 until preference.preferenceCount) {
                    tintPreferenceIcon(
                        preference.getPreference(j),
                        context, res, iconStyle, randomColors, cornerStyle
                    )
                }
            }
        }
    }

    @JvmStatic
    fun tintSinglePreferenceIcon(preference: Preference?, context: Context?) {
        if (preference == null || context == null) return
        if (preference.icon == null) return

        val iconStyle = Settings.System.getIntForUser(
            context.contentResolver,
            ICON_STYLE,
            ICON_STYLE_MATERIAL_EXPRESSIVE_ICON,
            UserHandle.USER_CURRENT
        )

        val randomColors = Settings.System.getIntForUser(
            context.contentResolver,
            ICON_RANDOM_COLORS,
            0,
            UserHandle.USER_CURRENT
        ) == 1

        val cornerStyle = Settings.System.getIntForUser(
            context.contentResolver,
            ICON_CORNER_STYLE,
            CORNER_STYLE_ROUND,
            UserHandle.USER_CURRENT
        )

        tintPreferenceIcon(
            preference, context, context.resources,
            iconStyle, randomColors, cornerStyle
        )
    }

    private fun unwrapToOriginalIcon(drawable: Drawable): Drawable {
        if (drawable is LayerDrawable) {
            val inner = drawable.findDrawableByLayerId(ICON_LAYER_ID)
            if (inner != null) {
                return unwrapToOriginalIcon(inner)
            }
        }
        return drawable
    }

    private fun tintPreferenceIcon(
        preference: Preference,
        context: Context,
        res: Resources,
        iconStyle: Int,
        randomColors: Boolean,
        cornerStyle: Int
    ) {
        val rawIcon: Drawable = preference.icon ?: return
        val icon: Drawable = unwrapToOriginalIcon(rawIcon).mutate()

        val colors: IconColors = if (randomColors) {
            getRandomColors(res)
        } else {
            getCachedColorsForPreference(preference.key, res)
        }

        val cornerRadiusDp = if (cornerStyle == CORNER_STYLE_ROUND) {
            ROUND_CORNER_RADIUS_DP
        } else {
            SQUARISH_CORNER_RADIUS_DP
        }

        when (iconStyle) {
            ICON_STYLE_MATERIAL_EXPRESSIVE_ICON -> {
                applyIconWithBackground(
                    preference, icon, colors.bg,
                    iconColor = colors.fg,
                    cornerRadiusDp = cornerRadiusDp,
                    context = context
                )
            }

            ICON_STYLE_SOLID_BG_WHITE_ICON -> {
                val saturatedBgColor = increaseSaturation(colors.bg, SOLID_BG_SATURATION_BOOST)
                applyIconWithBackground(
                    preference, icon, saturatedBgColor,
                    iconColor = Color.WHITE,
                    cornerRadiusDp = cornerRadiusDp,
                    context = context
                )
            }

            ICON_STYLE_GRADIENT_BG_WHITE_ICON -> {
                applyIconWithGradientBackground(
                    preference, icon, colors.bg,
                    cornerRadiusDp, context
                )
            }

            ICON_STYLE_COLOR_ICON_NO_BG -> {
                icon.setTint(colors.bg)
                icon.setTintMode(PorterDuff.Mode.SRC_ATOP)
                preference.icon = icon
            }

            ICON_STYLE_ACCENT_OUTLINE_ACCENT_ICON -> {
                applyIconWithOutline(
                    preference, icon,
                    outlineColor = resolveThemeColorAccent(context),
                    fillColor = Color.TRANSPARENT,
                    cornerRadiusDp = cornerRadiusDp,
                    context = context,
                    useAccentForIcon = true
                )
            }

            ICON_STYLE_SOLID_OUTLINE_SOLID_ICON -> {
                applyIconWithOutline(
                    preference, icon,
                    outlineColor = colors.bg,
                    fillColor = Color.TRANSPARENT,
                    cornerRadiusDp = cornerRadiusDp,
                    context = context,
                    useAccentForIcon = false
                )
            }

            ICON_STYLE_ACCENT_ICON -> {
                val accentColor = resolveThemeColorAccent(context)
                icon.setTint(accentColor)
                icon.setTintMode(PorterDuff.Mode.SRC_ATOP)
                preference.icon = icon
            }

            else -> {
                val accentColor = resolveThemeColorAccent(context)
                icon.setTint(accentColor)
                icon.setTintMode(PorterDuff.Mode.SRC_ATOP)
                preference.icon = icon
            }
        }
    }

    private fun applyIconWithBackground(
        preference: Preference,
        icon: Drawable,
        bgColor: Int,
        iconColor: Int,
        cornerRadiusDp: Int,
        context: Context
    ) {
        val density = context.resources.displayMetrics.density
        val padding = (BG_PADDING_DP * density).toInt()
        val cornerRadius = (cornerRadiusDp * density).toInt()

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius.toFloat()
            setColor(bgColor)
        }

        icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)

        val layerDrawable = LayerDrawable(arrayOf<Drawable>(bgDrawable, icon)).apply {
            setId(1, ICON_LAYER_ID)
            setLayerInset(1, padding, padding, padding, padding)
        }

        icon.setTint(iconColor)
        icon.setTintMode(PorterDuff.Mode.SRC_ATOP)

        preference.icon = layerDrawable
    }

    private fun applyIconWithGradientBackground(
        preference: Preference,
        icon: Drawable,
        baseColor: Int,
        cornerRadiusDp: Int,
        context: Context
    ) {
        val density = context.resources.displayMetrics.density
        val padding = (BG_PADDING_DP * density).toInt()
        val cornerRadius = (cornerRadiusDp * density).toInt()

        val startColor = adjustSaturation(
            lightenColor(baseColor, GRADIENT_COLOR_FACTOR),
            -GRADIENT_SATURATION_FACTOR
        )
        val endColor = adjustSaturation(
            darkenColor(baseColor, GRADIENT_SATURATION_FACTOR),
            GRADIENT_COLOR_FACTOR
        )
        val gradientColors = intArrayOf(startColor, baseColor, endColor)

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius.toFloat()
            colors = gradientColors
            orientation = GradientDrawable.Orientation.TL_BR
            gradientType = GradientDrawable.LINEAR_GRADIENT
            setGradientCenter(0.5f, 0.5f)
        }

        icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)

        val layerDrawable = LayerDrawable(arrayOf<Drawable>(bgDrawable, icon)).apply {
            setId(1, ICON_LAYER_ID)
            setLayerInset(1, padding, padding, padding, padding)
        }

        icon.setTint(Color.WHITE)
        icon.setTintMode(PorterDuff.Mode.SRC_ATOP)

        preference.icon = layerDrawable
    }

    private fun applyIconWithOutline(
        preference: Preference,
        icon: Drawable,
        outlineColor: Int,
        fillColor: Int,
        cornerRadiusDp: Int,
        context: Context,
        useAccentForIcon: Boolean
    ) {
        val density = context.resources.displayMetrics.density
        val padding = (BG_PADDING_DP * density).toInt()
        val outlineWidth = (OUTLINE_WIDTH_DP * density).toInt()
        val cornerRadius = (cornerRadiusDp * density).toInt()

        val outlineDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius.toFloat()
            setColor(fillColor)
            setStroke(outlineWidth, outlineColor)
        }

        icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)

        val layerDrawable = LayerDrawable(arrayOf<Drawable>(outlineDrawable, icon)).apply {
            setId(1, ICON_LAYER_ID)
            setLayerInset(1, padding, padding, padding, padding)
        }

        val iconColor = if (useAccentForIcon) resolveThemeColorAccent(context) else outlineColor
        icon.setTint(iconColor)
        icon.setTintMode(PorterDuff.Mode.SRC_ATOP)

        preference.icon = layerDrawable
    }

    @ColorInt
    private fun adjustSaturation(@ColorInt color: Int, saturationDelta: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] + saturationDelta).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun resolveThemeColorAccent(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }

    private fun getCachedColorsForPreference(key: String?, res: Resources): IconColors {
        val safeKey = key ?: ""
        colorCache[safeKey]?.let { return it }

        val colorIndex = abs(safeKey.hashCode()) % MATERIAL_COLOR_BG_RES_IDS.size
        val bg = res.getColor(MATERIAL_COLOR_BG_RES_IDS[colorIndex], null)
        val fg = res.getColor(MATERIAL_COLOR_FG_RES_IDS[colorIndex], null)
        val colors = IconColors(bg, fg)
        colorCache[safeKey] = colors
        return colors
    }

    private fun getRandomColors(res: Resources): IconColors {
        val colorIndex = Random.nextInt(MATERIAL_COLOR_BG_RES_IDS.size)
        val bg = res.getColor(MATERIAL_COLOR_BG_RES_IDS[colorIndex], null)
        val fg = res.getColor(MATERIAL_COLOR_FG_RES_IDS[colorIndex], null)
        return IconColors(bg, fg)
    }

    @ColorInt
    private fun increaseSaturation(@ColorInt color: Int, saturationBoost: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] + saturationBoost).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
    }

    @ColorInt
    private fun lightenColor(@ColorInt color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + factor).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
    }

    @ColorInt
    private fun darkenColor(@ColorInt color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] - factor).coerceAtLeast(0f)
        return Color.HSVToColor(hsv)
    }

    class SettingsObserver(
        handler: Handler,
        private val context: Context,
        private val screen: PreferenceScreen
    ) : ContentObserver(handler) {

        fun register() {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(ICON_STYLE),
                false, this, UserHandle.USER_CURRENT
            )
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(ICON_RANDOM_COLORS),
                false, this, UserHandle.USER_CURRENT
            )
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(ICON_CORNER_STYLE),
                false, this, UserHandle.USER_CURRENT
            )
        }

        fun unregister() {
            context.contentResolver.unregisterContentObserver(this)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri == null) return
            if (uri == Settings.System.getUriFor(ICON_STYLE) ||
                uri == Settings.System.getUriFor(ICON_RANDOM_COLORS) ||
                uri == Settings.System.getUriFor(ICON_CORNER_STYLE)
            ) {
                colorCache.clear()
                tintIcons(screen, context)
            }
        }
    }
}
