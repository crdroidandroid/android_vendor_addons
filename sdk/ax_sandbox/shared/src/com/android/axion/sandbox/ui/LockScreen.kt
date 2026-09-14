package com.android.axion.sandbox.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType.FACE
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType.NONE
import com.android.axion.sandbox.shared.R
import com.android.axion.sandbox.ui.BouncerPrompt
import com.android.axion.sandbox.ui.PinInputDisplay
import com.android.axion.sandbox.ui.ZoomFadeContainer
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    isSetup: Boolean = false,
    promptText: String? = null,
    onUnlock: () -> Unit,
    onPinEntered: (String) -> Boolean,
    confirmPin: String? = null,
    onBack: (() -> Unit)? = null,
    biometricType: BiometricType = NONE,
    onBiometricClick: () -> Unit = {},
    onForgotPassword: (() -> Unit)? = null,
    isExiting: Boolean = false
) {
    val enteredPin = remember { mutableStateOf("") }
    val isError = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val errorMismatch = stringResource(R.string.pin_error_mismatch)
    val errorIncorrect = stringResource(R.string.pin_error_incorrect)

    val buttonScaleAnimatables = remember { List(12) { Animatable(1f) } }

    LaunchedEffect(confirmPin) {
        enteredPin.value = ""
        isError.value = false
    }

    LaunchedEffect(isError.value) {
        if (isError.value) {
            triggerFailureAnimation(buttonScaleAnimatables)
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
            enteredPin.value = ""
        }
    ).value

    val shakeTranslation = if (isError.value) (sin(shakeOffset * 4 * PI.toFloat()) * 20f) else 0f

    LaunchedEffect(enteredPin.value) {
        if (enteredPin.value.length != 4) return@LaunchedEffect
        delay(100)
        if (onPinEntered(enteredPin.value)) {
            onUnlock()
            return@LaunchedEffect
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        errorMessage.value = getPinErrorMessage(isSetup, confirmPin != null, errorMismatch, errorIncorrect)
        isError.value = true
    }

    val titleText = when {
        !isSetup -> stringResource(R.string.pin_title_enter)
        confirmPin == null -> stringResource(R.string.pin_title_create)
        else -> stringResource(R.string.pin_title_confirm)
    }

    val promptSubtitle = when {
        isError.value -> errorMessage.value
        promptText != null -> promptText
        !isSetup -> stringResource(R.string.pin_prompt_unlock)
        confirmPin == null -> stringResource(R.string.pin_prompt_create)
        else -> stringResource(R.string.pin_prompt_confirm)
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

                PinInputDisplay(
                    pinLength = enteredPin.value.length,
                    maxDigits = 4,
                    hasError = isError.value,
                    displayModifier = Modifier.graphicsLayer { translationX = shakeTranslation }
                )

                Spacer(modifier = Modifier.height(48.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    ).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            row.forEachIndexed { colIndex, digit ->
                                val buttonIndex = rowIndex * 3 + colIndex
                                NumberButton(
                                    text = digit,
                                    scaling = buttonScaleAnimatables[buttonIndex].value,
                                    onButtonClick = {
                                        if (enteredPin.value.length < 4 && !isError.value) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            enteredPin.value += digit
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isSetup && biometricType != NONE) {
                                BiometricKeyButton(
                                    biometricType = biometricType,
                                    scale = buttonScaleAnimatables[9].value,
                                    onClick = onBiometricClick
                                )
                            }
                        }

                        NumberButton(
                            text = "0",
                            scaling = buttonScaleAnimatables[10].value,
                            onButtonClick = {
                                if (enteredPin.value.length < 4 && !isError.value) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    enteredPin.value += "0"
                                }
                            }
                        )

                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BackspaceKeyButton(
                                scale = buttonScaleAnimatables[11].value,
                                onClick = {
                                    if (enteredPin.value.isNotEmpty() && !isError.value) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        enteredPin.value = enteredPin.value.dropLast(1)
                                    }
                                }
                            )
                        }
                    }
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

private suspend fun triggerFailureAnimation(
    animatables: List<Animatable<Float, AnimationVector1D>>
) {
    coroutineScope {
        animatables.forEachIndexed { index, animatable ->
            launch {
                animatable.animateTo(
                    targetValue = 0.78f,
                    animationSpec = tween(
                        durationMillis = 50,
                        delayMillis = index * 33,
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

@Composable
private fun NumberButton(
    text: String,
    scaling: Float = 1f,
    onButtonClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState().value

    val animEasing = if (isPressed) LinearEasing else FastOutSlowInEasing
    val animDuration = if (isPressed) 100 else 420

    val cornerRadius = animateDpAsState(
        targetValue = if (isPressed) 18.dp else 36.dp,
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "buttonCornerRadius"
    ).value

    val containerColor = animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceBright,
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "buttonContainerColor"
    ).value

    val contentColor = animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "buttonContentColor"
    ).value

    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scaling
                scaleY = scaling
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onButtonClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            style = MaterialTheme.typography.headlineMedium,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

private fun getPinErrorMessage(
    isSetup: Boolean,
    hasConfirmPin: Boolean,
    mismatch: String,
    incorrect: String
): String {
    if (isSetup && hasConfirmPin) return mismatch
    return incorrect
}

@Composable
private fun BiometricKeyButton(
    biometricType: BiometricType,
    scale: Float,
    onClick: () -> Unit
) {
    val icons = Icons.Filled
    val icon = if (biometricType == FACE) icons.Face else icons.Fingerprint
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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

@Composable
private fun BackspaceKeyButton(
    scale: Float,
    onClick: () -> Unit
) {
    val buttonModifier = Modifier
        .size(56.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)

    IconButton(
        onClick = onClick,
        modifier = buttonModifier
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.action_backspace),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
