package com.android.axion.kotlin.math

import android.content.Context
import kotlin.math.min
import kotlin.math.roundToInt

fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

val Context.scaleRatioLocked: Float
    get() {
        val dm = resources.displayMetrics
        val sw = min(dm.widthPixels, dm.heightPixels) / dm.density
        return sw / 420f
    }

val Context.scaleRatio: Float
    get() {
        val dm = resources.displayMetrics
        val sw = min(dm.widthPixels, dm.heightPixels) / dm.density
        return if (sw > 620f) 1f else sw / 420f
    }

fun Context.sldp(value: Number): Float {
    return value.toFloat() * scaleRatioLocked
}

fun Context.sdp(value: Number): Float {
    return value.toFloat() * scaleRatio
}

fun Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()

fun Context.dpToPx(dp: Float): Int =
    (dp * resources.displayMetrics.density).toInt()

fun Context.dpToPxF(dp: Int): Float =
    dp * resources.displayMetrics.density

fun Context.dpToPxF(dp: Float): Float =
    dp * resources.displayMetrics.density

fun dpToPx(dp: Int, densityDpi: Int): Int =
    (dp * (densityDpi / 160f)).roundToInt()

