package com.android.axion.kotlin.math

import android.content.Context
import android.content.res.Resources
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.Number

fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

val Context.scaleRatioLocked: Float
    get() = resources.scaleRatioLocked

val Resources.scaleRatioLocked: Float
    get() {
        val dm = displayMetrics
        val sw = min(dm.widthPixels, dm.heightPixels) / dm.density
        return sw / 420f
    }

val Context.scaleRatio: Float
    get() = resources.scaleRatio

val Resources.scaleRatio: Float
    get() {
        val dm = displayMetrics
        val sw = min(dm.widthPixels, dm.heightPixels) / dm.density
        return if (sw > 620f) 1f else sw / 420f
    }

fun Context.sldp(value: Number): Float = resources.sldp(value)

fun Resources.sldp(value: Number): Float {
    return value.toFloat() * scaleRatioLocked
}

fun Context.sdp(value: Number): Float = resources.sdp(value)

fun Resources.sdp(value: Number): Float {
    return value.toFloat() * scaleRatio
}

fun Context.dpToPx(dp: Int): Int = resources.dpToPx(dp)

fun Resources.dpToPx(dp: Int): Int =
    (dp * displayMetrics.density).toInt()

fun Context.dpToPx(dp: Float): Int = resources.dpToPx(dp)

fun Resources.dpToPx(dp: Float): Int =
    (dp * displayMetrics.density).toInt()

fun Context.dpToPxF(dp: Int): Float = resources.dpToPxF(dp)

fun Resources.dpToPxF(dp: Int): Float =
    dp * displayMetrics.density

fun Context.dpToPxF(dp: Float): Float = resources.dpToPxF(dp)

fun Resources.dpToPxF(dp: Float): Float =
    dp * displayMetrics.density

fun dpToPx(dp: Int, densityDpi: Int): Int =
    (dp * (densityDpi / 160f)).roundToInt()
