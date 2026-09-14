package com.android.axion.sandbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class BouncerPromptMessage(
    val title: String,
    val subtitle: String? = null,
    val isError: Boolean = false
)

@Composable
fun BouncerPrompt(
    title: String,
    subtitle: String? = null,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val message = remember(title, subtitle, isError) {
        BouncerPromptMessage(title = title, subtitle = subtitle, isError = isError)
    }

    Crossfade(
        targetState = message,
        label = "BouncerPromptCrossfade",
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        modifier = modifier.fillMaxWidth()
    ) { currentMessage ->
        PromptContent(currentMessage)
    }
}

@Composable
private fun PromptContent(message: BouncerPromptMessage) {
    val titleColor = if (message.isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (message.isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = message.title,
            style = MaterialTheme.typography.headlineMedium,
            color = titleColor,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )

        message.subtitle?.takeIf { it.isNotEmpty() }?.let {
            SubtitleText(subtitle = it, color = subtitleColor)
        }
    }
}

@Composable
private fun SubtitleText(subtitle: String, color: Color) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 2
    )
}

@Composable
fun ZoomFadeContainer(
    isExiting: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            launch {
                scale.animateTo(
                    targetValue = 0.92f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                )
            }
        } else {
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        }
    ) {
        content()
    }
}
