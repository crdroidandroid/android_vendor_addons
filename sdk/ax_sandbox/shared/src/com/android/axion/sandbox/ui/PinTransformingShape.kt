package com.android.axion.sandbox.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun PinInputDisplay(
    pinLength: Int,
    maxDigits: Int = 4,
    hasError: Boolean = false,
    displayModifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = displayModifier
    ) {
        repeat(maxDigits) { index ->
            RenderSlot(index, pinLength, hasError)
        }
    }
}

@Composable
private fun RenderSlot(index: Int, pinLength: Int, hasError: Boolean) {
    PinTransformingSlot(
        slotIndex = index,
        isFilled = index < pinLength,
        isError = hasError
    )
}

@Composable
fun PinTransformingSlot(
    slotIndex: Int,
    isFilled: Boolean,
    isError: Boolean,
    slotModifier: Modifier = Modifier
) {
    val morphProgress = remember { Animatable(if (isFilled) 1f else 0f) }
    val scaleAnim = remember { Animatable(if (isFilled) 1f else 0.8f) }
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isFilled) {
        if (isFilled) {
            animateSlotEntry(slotIndex, morphProgress, scaleAnim, rotationAnim)
        } else {
            animateSlotExit(morphProgress, scaleAnim)
        }
    }

    val targetColor = when {
        isError -> MaterialTheme.colorScheme.error
        isFilled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val animatedColor = animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "slotColor"
    ).value

    Box(
        modifier = slotModifier
            .size(24.dp)
            .clipToBounds()
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                rotationZ = rotationAnim.value
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val origin = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2.4f

            if (!isFilled && morphProgress.value == 0f) {
                drawCircle(
                    color = animatedColor,
                    radius = radius * 0.75f,
                    center = origin,
                    style = Stroke(width = 2.dp.toPx())
                )
                return@Canvas
            }

            val path = createMorphingPath(
                slotIndex = slotIndex,
                progress = morphProgress.value,
                radius = radius,
                center = origin
            )
            drawPath(path = path, color = animatedColor)
        }
    }
}

private fun getInitialRotation(slotIndex: Int): Float = when (slotIndex % 4) {
    0 -> 45f
    1 -> -30f
    2 -> 45f
    else -> -15f
}

private fun createMorphingPath(
    slotIndex: Int,
    progress: Float,
    radius: Float,
    center: Offset
): Path {
    val path = Path()
    val steps = 72
    var firstPoint = true

    for (i in 0 until steps) {
        val angle = (2 * PI * i / steps).toFloat()
        val r = calculateRadiusForShape(slotIndex % 4, angle, progress, radius)
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)

        if (firstPoint) {
            path.moveTo(x, y)
            firstPoint = false
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}

private fun calculateRadiusForShape(
    shapeType: Int,
    angle: Float,
    progress: Float,
    baseRadius: Float
): Float = when (shapeType) {
    0 -> {
        val starOffset = (1f - progress) * 0.35f * cos(4f * angle)
        baseRadius * (1f - (1f - progress) * 0.15f + starOffset)
    }
    1 -> {
        val flowerOffset = (1f - progress) * 0.22f * cos(6f * angle)
        baseRadius * (1f - (1f - progress) * 0.10f + flowerOffset)
    }
    2 -> {
        val n = 1.2f + 0.8f * progress
        val cosPart = abs(cos(angle)).pow(n)
        val sinPart = abs(sin(angle)).pow(n)
        val denom = (cosPart + sinPart).pow(1f / n)
        if (denom > 0.001f) (baseRadius / denom) else baseRadius
    }
    else -> {
        val n = 4.0f - 2.0f * progress
        val cosPart = abs(cos(angle)).pow(n)
        val sinPart = abs(sin(angle)).pow(n)
        val denom = (cosPart + sinPart).pow(1f / n)
        if (denom > 0.001f) (baseRadius / denom) else baseRadius
    }
}

private suspend fun animateSlotEntry(
    slotIndex: Int,
    morphProgress: Animatable<Float, *>,
    scaleAnim: Animatable<Float, *>,
    rotationAnim: Animatable<Float, *>
) = coroutineScope {
    launch { runMorph(morphProgress) }
    launch { runScale(scaleAnim) }
    launch { runRotation(slotIndex, rotationAnim) }
}

private suspend fun runMorph(animatable: Animatable<Float, *>) {
    animatable.snapTo(0f)
    animatable.animateTo(1f, tween(durationMillis = 350, easing = FastOutSlowInEasing))
}

private suspend fun runScale(animatable: Animatable<Float, *>) {
    animatable.snapTo(0.4f)
    animatable.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
}

private suspend fun runRotation(slotIndex: Int, animatable: Animatable<Float, *>) {
    animatable.snapTo(getInitialRotation(slotIndex))
    animatable.animateTo(0f, tween(durationMillis = 350, easing = FastOutSlowInEasing))
}

private suspend fun animateSlotExit(
    morphProgress: Animatable<Float, *>,
    scaleAnim: Animatable<Float, *>
) = coroutineScope {
    launch { morphProgress.animateTo(0f, tween(durationMillis = 150)) }
    launch { scaleAnim.animateTo(0.8f, tween(durationMillis = 150)) }
}
