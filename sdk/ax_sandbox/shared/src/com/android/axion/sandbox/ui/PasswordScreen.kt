package com.android.axion.sandbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType.FACE
import com.android.axion.sandbox.security.SandboxSecurityManager.BiometricType.NONE
import com.android.axion.sandbox.shared.R
import com.android.axion.sandbox.ui.BouncerPrompt
import com.android.axion.sandbox.ui.ZoomFadeContainer
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PasswordScreen(
    isSetup: Boolean = false,
    promptText: String? = null,
    onUnlock: () -> Unit,
    onPasswordEntered: (String) -> Boolean,
    confirmPassword: String? = null,
    onBack: (() -> Unit)? = null,
    biometricType: BiometricType = NONE,
    onBiometricClick: () -> Unit = {},
    onForgotPassword: (() -> Unit)? = null,
    isExiting: Boolean = false,
    requestInitialFocus: Boolean = true
) {
    val enteredPassword = remember { mutableStateOf("") }
    val isError = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }
    val isPasswordVisible = remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val errorMismatch = stringResource(R.string.password_error_mismatch)
    val errorIncorrect = stringResource(R.string.password_error_incorrect)

    LaunchedEffect(requestInitialFocus) {
        if (!requestInitialFocus) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(confirmPassword) {
        enteredPassword.value = ""
        isError.value = false
    }

    val shakeOffset = animateFloatAsState(
        targetValue = if (isError.value) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "shake",
        finishedListener = {
            if (isError.value) isError.value = false
        }
    ).value

    val shakeTranslation = if (isError.value) (sin(shakeOffset * 4 * PI.toFloat()) * 16f) else 0f

    val submitAction: () -> Unit = {
        if (enteredPassword.value.length >= 4) {
            if (onPasswordEntered(enteredPassword.value)) {
                onUnlock()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isError.value = true
                errorMessage.value = getPasswordErrorMessage(isSetup, confirmPassword != null, errorMismatch, errorIncorrect)
            }
        }
    }

    val titleText = when {
        !isSetup -> stringResource(R.string.password_title_enter)
        confirmPassword == null -> stringResource(R.string.password_title_create)
        else -> stringResource(R.string.password_title_confirm)
    }

    val promptSubtitle = when {
        isError.value -> errorMessage.value
        promptText != null -> promptText
        !isSetup -> stringResource(R.string.password_prompt_unlock)
        confirmPassword == null -> stringResource(R.string.password_prompt_create)
        else -> stringResource(R.string.password_prompt_confirm)
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
                    .widthIn(max = 400.dp)
            ) {
                BouncerPrompt(
                    title = titleText,
                    subtitle = promptSubtitle,
                    isError = isError.value,
                    modifier = Modifier.graphicsLayer { translationX = shakeTranslation }
                )

                Spacer(modifier = Modifier.height(48.dp))

                OutlinedTextField(
                    value = enteredPassword.value,
                    onValueChange = {
                        enteredPassword.value = it
                        if (isError.value) isError.value = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .graphicsLayer { translationX = shakeTranslation },
                    label = { Text(stringResource(R.string.password_label)) },
                    placeholder = { Text(stringResource(R.string.password_placeholder)) },
                    singleLine = true,
                    isError = isError.value,
                    shape = RoundedCornerShape(28.dp),
                    visualTransformation = if (isPasswordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitAction() }),
                    trailingIcon = {
                        PasswordVisibilityToggle(
                            isVisible = isPasswordVisible.value,
                            onToggle = { isPasswordVisible.value = !isPasswordVisible.value }
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { submitAction() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    enabled = enteredPassword.value.length >= 4
                ) {
                    val buttonText = when {
                        !isSetup -> stringResource(R.string.action_continue)
                        confirmPassword == null -> stringResource(R.string.action_continue)
                        else -> stringResource(R.string.action_set_password)
                    }
                    Text(text = buttonText)
                }

                if (!isSetup && biometricType != NONE) {
                    PasswordBiometricButton(
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

@Composable
private fun PasswordVisibilityToggle(
    isVisible: Boolean,
    onToggle: () -> Unit
) {
    val icons = Icons.Filled
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isVisible) icons.VisibilityOff else icons.Visibility,
            contentDescription = stringResource(
                if (isVisible) R.string.action_hide_password else R.string.action_show_password
            )
        )
    }
}

@Composable
private fun PasswordBiometricButton(
    biometricType: BiometricType,
    onClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
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

private fun getPasswordErrorMessage(
    isSetup: Boolean,
    hasConfirmPassword: Boolean,
    mismatch: String,
    incorrect: String
): String {
    if (isSetup && hasConfirmPassword) return mismatch
    return incorrect
}
