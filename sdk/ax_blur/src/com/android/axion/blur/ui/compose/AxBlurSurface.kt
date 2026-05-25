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

import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.toSize
import com.android.axion.blur.BlurEngine
import com.android.axion.blur.settings.AxBackdropBlurSettingsSpec
import com.android.axion.kotlin.settings.SettingsFlow
import com.android.axion.kotlin.settings.SettingsType
import kotlin.math.roundToInt

val LocalAxBlurEnabled = staticCompositionLocalOf { true }

object AxBlurSurfaceDefaults {
    @Composable
    fun surfaceColor(): Color = colorResource(R.color.ax_blur_surface_bright_expressive)

    @Composable
    fun surfaceColor(alpha: Float): Color =
        surfaceColor().copy(alpha = alpha.coerceIn(0f, 1f))

    @Composable
    fun tintColor(surfaceColor: Color? = null): Color {
        val tint = colorResource(R.color.ax_blur_surface_bright_expressive_tint)
        return surfaceColor?.copy(alpha = tint.alpha) ?: tint
    }

    @Composable
    fun tintColor(alpha: Float, surfaceColor: Color? = null): Color {
        val tint = tintColor(surfaceColor)
        return tint.copy(alpha = tint.alpha * alpha.coerceIn(0f, 1f))
    }
}

@Composable
fun AxBlurLifecycle(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val parentEnabled = LocalAxBlurEnabled.current
    CompositionLocalProvider(
        LocalAxBlurEnabled provides (parentEnabled && enabled),
        content = content,
    )
}

@Composable
fun rememberAxBlurEnabled(
    settingsSpec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
): Boolean {
    val resolver = LocalContext.current.contentResolver
    val globalSettings = remember(resolver) { SettingsFlow(resolver, SettingsType.GLOBAL) }
    val globalDefaultDisabled = !AxBlurProperties.defaultGlobalBlurEnabled
    val globalDisabled by remember(globalSettings, globalDefaultDisabled) {
        globalSettings.observeBoolean(Settings.Global.DISABLE_WINDOW_BLURS, globalDefaultDisabled)
    }.collectAsState(
        initial = globalSettings.getInt(
            Settings.Global.DISABLE_WINDOW_BLURS,
            if (globalDefaultDisabled) 1 else 0,
        ) != 0,
    )
    val enabledKey = settingsSpec.enabledKey
    val settingEnabled = if (enabledKey == null) {
        true
    } else {
        val secureSettings = remember(resolver) { SettingsFlow(resolver, SettingsType.SECURE) }
        val value by remember(secureSettings, enabledKey, settingsSpec.defaultEnabled) {
            secureSettings.observeBoolean(enabledKey, settingsSpec.defaultEnabled)
        }.collectAsState(
            initial = secureSettings.getInt(
                enabledKey,
                if (settingsSpec.defaultEnabled) 1 else 0,
            ) != 0,
        )
        value
    }
    return !globalDisabled && settingEnabled
}

@Composable
fun AxBlurSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    cornerRadius: Dp,
    enabled: Boolean = true,
    surfaceColor: Color? = null,
    tintColor: Color? = null,
    settingsSpec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
    contentAlignment: Alignment = Alignment.TopStart,
    alpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val resolvedSurfaceColor = surfaceColor ?: AxBlurSurfaceDefaults.surfaceColor()
    val resolvedTintColor = tintColor ?: AxBlurSurfaceDefaults.tintColor(surfaceColor)
    Box(
        modifier = modifier
            .clip(shape)
            .axBlurBackground(
                enabled = enabled,
                fallbackColor = resolvedSurfaceColor,
                tintColor = resolvedTintColor,
                cornerRadius = cornerRadius,
                settingsSpec = settingsSpec,
                alpha = alpha,
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun Modifier.axBlurBackground(
    enabled: Boolean,
    fallbackColor: Color,
    tintColor: Color,
    cornerRadius: Dp = Dp.Unspecified,
    settingsSpec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
    alpha: Float = 1f,
): Modifier {
    val density = LocalDensity.current
    val view = LocalView.current
    val blurEnabled = LocalAxBlurEnabled.current && enabled
    val blur = remember(view, settingsSpec) {
        BlurEngine(view).apply {
            useSettings(settingsSpec)
            setEnabled(blurEnabled)
        }
    }
    val tintArgb = tintColor.toArgb()
    val requestedCornerRadiusPx = if (cornerRadius.isSpecified) {
        with(density) { cornerRadius.toPx() }
    } else {
        Float.NaN
    }
    val blurAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()

    DisposableEffect(blur) {
        onDispose(blur::dispose)
    }

    SideEffect {
        blur.setEnabled(blurEnabled)
    }

    return axBlurFrameTracking(blurEnabled).drawWithContent {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        val cornerRadiusPx = if (requestedCornerRadiusPx.isFinite()) {
            requestedCornerRadiusPx
        } else {
            size.minDimension * 0.5f
        }
        val drewBlur = if (blurEnabled && width > 0 && height > 0) {
            blur.setAlpha(blurAlpha)
            blur.setOverlayColor(tintArgb)
            var result = false
            drawIntoCanvas { canvas ->
                result = blur.draw(
                    canvas.nativeCanvas,
                    0,
                    0,
                    width,
                    height,
                    cornerRadiusPx,
                )
            }
            result
        } else {
            false
        }
        if (!drewBlur) {
            drawRoundRect(
                color = fallbackColor,
                cornerRadius = CornerRadius(cornerRadiusPx),
            )
        }
        drawContent()
    }
}

private fun Modifier.axBlurFrameTracking(enabled: Boolean): Modifier =
    this then AxBlurFrameTrackingElement(enabled)

private data class AxBlurFrameTrackingElement(
    val enabled: Boolean,
) : ModifierNodeElement<AxBlurFrameTrackingNode>() {
    override fun create() = AxBlurFrameTrackingNode(enabled)

    override fun update(node: AxBlurFrameTrackingNode) {
        node.update(enabled)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "axBlurFrameTracking"
        properties["enabled"] = enabled
    }
}

private class AxBlurFrameTrackingNode(
    private var enabled: Boolean,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    LayoutAwareModifierNode,
    ObserverModifierNode {

    private var lastCoordinates: LayoutCoordinates? = null
    private var geometry = AxBlurFrameGeometry()
    private var hostView: View? = null
    private var observingPreDraw = false
    private var skipNextHostDirty = false
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (isAttached && enabled) {
            val viewDirty = hostView?.isDirty == true
            lastCoordinates?.let { updateCoordinates(it) }
            if (viewDirty) {
                if (skipNextHostDirty) {
                    skipNextHostDirty = false
                } else {
                    skipNextHostDirty = true
                    invalidateDraw()
                }
            } else {
                skipNextHostDirty = false
            }
        }
        true
    }

    override val shouldAutoInvalidate = false

    fun update(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        updatePreDrawObserver()
        invalidateDraw()
    }

    override fun onAttach() {
        onObservedReadsChanged()
    }

    override fun onDetach() {
        removePreDrawObserver()
        hostView = null
        lastCoordinates = null
        geometry = AxBlurFrameGeometry()
        skipNextHostDirty = false
    }

    override fun onObservedReadsChanged() {
        observeReads {
            updatePreDrawObserver()
            lastCoordinates?.let { updateCoordinates(it) }
        }
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        updateCoordinates(coordinates)
    }

    override fun onRemeasured(size: IntSize) {
        val newSize = size.toSize()
        val nextGeometry = geometry.copy(size = newSize)
        if (geometry != nextGeometry) {
            geometry = nextGeometry
            invalidateDraw()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        updateCoordinates(coordinates)
    }

    override fun ContentDrawScope.draw() {
        drawContent()
    }

    private fun updateCoordinates(coordinates: LayoutCoordinates) {
        lastCoordinates = coordinates
        val bounds = coordinates.boundsInRoot()
        val newPosition = bounds.topLeft
        val newSize = bounds.size
        val newWindowId = currentWindowId()
        val nextGeometry = AxBlurFrameGeometry(
            position = newPosition,
            size = newSize,
            windowId = newWindowId,
        )
        if (geometry != nextGeometry) {
            geometry = nextGeometry
            invalidateDraw()
        }
    }

    private fun currentWindowId(): Any? {
        return hostView?.windowId ?: currentValueOf(LocalView).windowId
    }

    private data class AxBlurFrameGeometry(
        val position: Offset = Offset.Unspecified,
        val size: Size = Size.Unspecified,
        val windowId: Any? = null,
    )

    private fun updatePreDrawObserver() {
        val view = if (isAttached && enabled) currentValueOf(LocalView) else null
        if (hostView !== view) {
            removePreDrawObserver()
            hostView = view
            skipNextHostDirty = false
        }
        if (view != null && !observingPreDraw) {
            view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            observingPreDraw = true
        }
        if (view == null) {
            removePreDrawObserver()
        }
    }

    private fun removePreDrawObserver() {
        if (!observingPreDraw) return
        hostView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDrawListener)
        observingPreDraw = false
    }
}
