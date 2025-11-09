package com.android.axion.compose.math

import android.content.Context

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Number.sdp: Dp
    @Composable get() = (this.toFloat() * LocalContext.current.scaleRatio).dp
    
fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)

val Context.scaleRatio: Float
    get() {
        val displayMetrics = resources.displayMetrics
        val sw = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels) / displayMetrics.density
        return if (sw > 620f) 1f else sw / 420f
    }
