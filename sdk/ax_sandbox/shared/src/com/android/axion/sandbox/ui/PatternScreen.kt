package com.android.axion.sandbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType.FACE
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType.NONE
import com.android.axion.sandbox.shared.R
import com.android.axion.sandbox.ui.BouncerPrompt
import com.android.axion.sandbox.ui.ZoomFadeContainer
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun PatternScreen(
    isSetup: Boolean = false,
    promptText: String? = null,
    onUnlock: () -> Unit,
    onPatternEntered: (List<Int>) -> Boolean,
    confirmPattern: List<Int>? = null,
    onBack: (() -> Unit)? = null,
    biometricType: BiometricType = NONE,
    onBiometricClick: () -> Unit = {},
    onForgotPassword: (() -> Unit)? = null,
    isExiting: Boolean = false
) {
    val selectedDots = remember { mutableStateOf<List<Int>>(emptyList()) }
    val currentTouchPosition = remember { mutableStateOf<Offset?>(null) }
    val isError = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }
    val dotPositions = remember { mutableStateOf<Map<Int, Offset>>(emptyMap()) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val errorMismatch = stringResource(R.string.pattern_error_mismatch)
    val errorIncorrect = stringResource(R.string.pattern_error_incorrect)
    val errorTooShort = stringResource(R.string.pattern_error_too_short)

    val dotScalingAnimatables = remember { List(9) { Animatable(1f) } }
    val dotAppearFadeAnimatables = remember { List(9) { Animatable(0f) } }
    val dotAppearOffsetAnimatables = remember { List(9) { Animatable(0f) } }

    LaunchedEffect(Unit) {
        showPatternEntryAnimation(dotAppearFadeAnimatables, dotAppearOffsetAnimatables)
    }

    LaunchedEffect(confirmPattern) {
        selectedDots.value = emptyList()
        isError.value = false
    }

    LaunchedEffect(isError.value) {
        if (isError.value) {
            showPatternFailureAnimation(dotScalingAnimatables)
        }
    }

    val shakeOffset = animateFloatAsState(
        targetValue = if (isError.value) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "shake",
        finishedListener = {
            if (!isError.value) return@animateFloatAsState
            isError.value = false
            selectedDots.value = emptyList()
            errorMessage.value = ""
        }
    ).value

    val shakeTranslation = if (isError.value) (sin(shakeOffset * 4 * PI.toFloat()) * 16f) else 0f

    fun submitPattern() {
        val currentSelected = selectedDots.value
        if (currentSelected.isEmpty()) return
        if (currentSelected.size < 4) {
            errorMessage.value = errorTooShort
            isError.value = true
            return
        }
        if (onPatternEntered(currentSelected)) {
            onUnlock()
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        errorMessage.value = getPatternErrorMessage(isSetup, confirmPattern != null, errorMismatch, errorIncorrect)
        isError.value = true
    }

    val density = LocalDensity.current
    val dotRadius = with(density) { 12.dp.toPx() }
    val touchRadius = with(density) { 40.dp.toPx() }

    val errorColor = MaterialTheme.colorScheme.error
    val lineColor = if (isError.value) errorColor else MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val dotInactiveColor = MaterialTheme.colorScheme.outlineVariant

    val titleText = when {
        !isSetup -> stringResource(R.string.pattern_title_enter)
        confirmPattern == null -> stringResource(R.string.pattern_title_create)
        else -> stringResource(R.string.pattern_title_confirm)
    }

    val promptSubtitle = when {
        isError.value -> errorMessage.value
        promptText != null -> promptText
        !isSetup -> stringResource(R.string.pattern_prompt_unlock)
        confirmPattern == null -> stringResource(R.string.pattern_prompt_create)
        else -> stringResource(R.string.pattern_prompt_confirm)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        ZoomFadeContainer(
            isExiting = isExiting,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                BouncerPrompt(
                    title = titleText,
                    subtitle = promptSubtitle,
                    isError = isError.value,
                    modifier = Modifier.graphicsLayer { translationX = shakeTranslation }
                )

                Spacer(modifier = Modifier.height(48.dp))

                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer { translationX = shakeTranslation }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isError.value = false
                                    errorMessage.value = ""
                                    selectedDots.value = emptyList()
                                    currentTouchPosition.value = offset

                                    val hitDot = findHitDot(offset, dotPositions.value, touchRadius)
                                    if (hitDot != null && !selectedDots.value.contains(hitDot)) {
                                        selectedDots.value = listOf(hitDot)
                                        animateDotSelection(hitDot, dotScalingAnimatables, scope)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDrag = { change, _ ->
                                    currentTouchPosition.value = change.position

                                    val hitDot = findHitDot(change.position, dotPositions.value, touchRadius)
                                    if (hitDot != null && !selectedDots.value.contains(hitDot)) {
                                        selectedDots.value = selectedDots.value + hitDot
                                        animateDotSelection(hitDot, dotScalingAnimatables, scope)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    currentTouchPosition.value = null
                                    submitPattern()
                                },
                                onDragCancel = {
                                    currentTouchPosition.value = null
                                    selectedDots.value = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridSize = 3
                        val cellSize = size.width / gridSize

                        val positions = mutableMapOf<Int, Offset>()
                        for (row in 0 until gridSize) {
                            for (col in 0 until gridSize) {
                                val dotIndex = row * gridSize + col
                                val center = Offset(
                                    x = col * cellSize + cellSize / 2,
                                    y = row * cellSize + cellSize / 2
                                )
                                positions[dotIndex] = center
                            }
                        }
                        dotPositions.value = positions

                        val currentSelectedDots = selectedDots.value
                        if (currentSelectedDots.size > 1) {
                            for (i in 0 until currentSelectedDots.size - 1) {
                                val from = positions[currentSelectedDots[i]]
                                val to = positions[currentSelectedDots[i + 1]]
                                if (from != null && to != null) {
                                    drawLine(
                                        color = lineColor,
                                        start = from,
                                        end = to,
                                        strokeWidth = 8.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        val touchPos = currentTouchPosition.value
                        if (currentSelectedDots.isNotEmpty() && touchPos != null) {
                            val lastDot = positions[currentSelectedDots.last()]
                            if (lastDot != null) {
                                drawLine(
                                    color = lineColor.copy(alpha = 0.5f),
                                    start = lastDot,
                                    end = touchPos,
                                    strokeWidth = 8.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        for ((index, dotCenter) in positions) {
                            val isSelected = currentSelectedDots.contains(index)
                            val color = when {
                                isError.value -> errorColor
                                isSelected -> dotColor
                                else -> dotInactiveColor
                            }

                            val dotScale = dotScalingAnimatables[index].value
                            val appearAlpha = dotAppearFadeAnimatables[index].value
                            val appearOffsetY = (1f - dotAppearOffsetAnimatables[index].value) * 30f
                            val animatedCenter = Offset(dotCenter.x, dotCenter.y + appearOffsetY)

                            drawCircle(
                                color = color.copy(alpha = (if (isSelected) 0.3f else 0.2f) * appearAlpha),
                                radius = dotRadius * 2.5f * dotScale,
                                center = animatedCenter
                            )

                            drawCircle(
                                color = color.copy(alpha = appearAlpha),
                                radius = (if (isSelected) dotRadius * 1.3f else dotRadius) * dotScale,
                                center = animatedCenter
                            )
                        }
                    }
                }

                if (!isSetup && biometricType != NONE) {
                    BiometricButton(
                        biometricType = biometricType,
                        onClick = onBiometricClick
                    )
                }

                if (!isSetup && onForgotPassword != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(onClick = onForgotPassword) {
                        Text(
                            text = stringResource(R.string.action_forgot_password),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun animateDotSelection(
    dotIndex: Int,
    animatables: List<Animatable<Float, AnimationVector1D>>,
    scope: CoroutineScope
) {
    scope.launch {
        animatables[dotIndex].animateTo(
            targetValue = 1.5f,
            animationSpec = tween(durationMillis = 83, easing = LinearEasing)
        )
        animatables[dotIndex].animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }
}

private suspend fun showPatternEntryAnimation(
    fadeAnimatables: List<Animatable<Float, AnimationVector1D>>,
    offsetAnimatables: List<Animatable<Float, AnimationVector1D>>
) {
    coroutineScope {
        fadeAnimatables.forEachIndexed { index, animatable ->
            val rowIndex = index / 3
            launch {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 450,
                        delayMillis = rowIndex * 33,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
        offsetAnimatables.forEachIndexed { index, animatable ->
            val rowIndex = index / 3
            launch {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 450 + (rowIndex * 33),
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }
}

private suspend fun showPatternFailureAnimation(
    animatables: List<Animatable<Float, AnimationVector1D>>
) {
    coroutineScope {
        animatables.forEachIndexed { index, animatable ->
            val rowIndex = index / 3
            launch {
                animatable.animateTo(
                    targetValue = 0.81f,
                    animationSpec = tween(
                        durationMillis = 50,
                        delayMillis = rowIndex * 33,
                        easing = LinearEasing
                    )
                )
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 617,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }
}

private fun getPatternErrorMessage(
    isSetup: Boolean,
    hasConfirmPattern: Boolean,
    mismatch: String,
    incorrect: String
): String {
    if (isSetup && hasConfirmPattern) return mismatch
    return incorrect
}

private fun findHitDot(
    position: Offset,
    dotPositions: Map<Int, Offset>,
    touchRadius: Float
): Int? {
    for ((index, center) in dotPositions) {
        val distance = sqrt(
            (position.x - center.x).pow(2) + (position.y - center.y).pow(2)
        )
        if (distance <= touchRadius) {
            return index
        }
    }
    return null
}

@Composable
private fun BiometricButton(
    biometricType: BiometricType,
    onClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))
    val icons = Icons.Filled
    val icon = if (biometricType == FACE) icons.Face else icons.Fingerprint
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.action_biometric_unlock),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp)
        )
    }
}
