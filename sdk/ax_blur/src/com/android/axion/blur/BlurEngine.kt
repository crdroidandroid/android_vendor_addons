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

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.ArrayMap
import android.util.ArraySet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.android.axion.blur.settings.AxBackdropBlurInteractor
import com.android.axion.blur.settings.AxBackdropBlurSettingsSpec
import com.android.axion.blur.settings.AxBackdropBlurSettingsSubscription
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import java.io.PrintWriter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class FrameRateTracker(private val maxSamples: Int = 60) {
    private val timestamps = LongArray(maxSamples)
    private var head = 0
    private var count = 0
    fun isLimited(windowMs: Long = 200L, now: Long = System.nanoTime() / 1_000_000): Boolean {
        if (count < maxSamples) return false
        return (now - timestamps[head]) <= windowMs
    }
    fun record(now: Long = System.nanoTime() / 1_000_000) {
        timestamps[head] = now
        head = (head + 1) % maxSamples
        count = minOf(count + 1, maxSamples)
    }
}

open class BlurEngine @JvmOverloads constructor(
    val view: View,
    val observeSettings: Boolean = true,
) {
    companion object {
        private const val DEFAULT_CAPTURE_SCALE = 1f
    }

    private val path = Path()
    private val rect = RectF()
    private val childRect = RectF()
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetRect = Rect()
    private val targetRectF = RectF()
    private val visibleTrackingRect = Rect()
    private val transformMatrix = Matrix()
    private val scaledCornerRadii = FloatArray(8)
    private val sourceBlurNode = RenderNode("AxBlurSource")
    private val defaultKey = Any()
    private val drawables = ArrayMap<Any, BackgroundBlurDrawable>()
    private val drawableAlphaStates = ArrayMap<Any, DrawableAlphaState>()
    private val resolvedDrawableAlphas = ArrayMap<Any, Int>()
    private val trackedStates = ArrayMap<View, ViewFrameState>()
    private val sourceContentViews = ArraySet<View>()
    private var sourceDrawStopBranch: View? = null
    private var settingsInteractor = AxBackdropBlurInteractor(view.context)
    private var settingsSubscription: AxBackdropBlurSettingsSubscription? = null
    private var observingPreDraw = false
    private var observingDraw = false
    private var attached = false
    private var visible = true
    private var enabled = false
    private var globalBlurEnabled = true
    private var useSettingsBlurRadius = true
    private var blurRadiusPx = 0f
    private var alpha = 255
    private var overlayColor = Color.TRANSPARENT
    private var sourceView: View? = null
    private var crossWindowAlphaSource: View? = null
    private var sourceBlurRecorded = false
    private var sourceBlurDirty = true
    private var isBackdropFrozen = false
    private var recordedSourceState = SourceRecord()
    private var sourceBlurEffect: RenderEffect? = null
    private var sourceBlurEffectRadius = -1f
    private var sourceBlurSaturation = 1f
    private var sourceBlurCurveBias = 0f
    private var sourceBlurMinX = 0f
    private var sourceBlurMaxX = 255f
    private var sourceBlurMinY = 0f
    private var sourceBlurMaxY = 255f
    private var captureScale = DEFAULT_CAPTURE_SCALE
    private val frameTracker = FrameRateTracker()
    private var lastBlurRadius = -1f
    private var lastFadeTop = -1f
    private var lastFadeBottom = -1f
    private val lastDrawBounds = Rect()
    private var lastDrawHardwareAccelerated = false
    private var lastDrawPath = "none"
    private var lastDrawSucceeded = false
    private var wasEnabled = false
    private var blurFraction = 1f
    private var baseBlurRadiusPx = 0f
    private var fadeTopRatio = 0f
    private var fadeBottomRatio = 1f
    private var fadeAngleRad = 0f
    private val fadeGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (enabled && visible) {
            frameTracker.record()
            val stateChanged = hasTrackedStateChanged()
            val alphaChanged = updateDrawableAlphas()
            if (stateChanged || alphaChanged) {
                invalidateHost()
            }
        }
        true
    }
    private val drawListener = ViewTreeObserver.OnDrawListener {
        if (enabled) {
            updateDrawableAlphas()
        }
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            onAttachedToWindow()
        }

        override fun onViewDetachedFromWindow(v: View) {
            onDetachedFromWindow()
        }
    }

    init {
        view.addOnAttachStateChangeListener(attachListener)
        if (view.isAttachedToWindow) {
            onAttachedToWindow()
        }
        applyBlurSettings()
    }

    private fun isCrossWindowBlurActive(): Boolean {
        return globalBlurEnabled &&
            hasRequiredWindowFocus() &&
            shouldTrackFrames() &&
            AxBlurSupport.supportsCrossWindowBlur()
    }

    fun isBlurActive(): Boolean {
        if (!attached || !view.isAttachedToWindow || AxBlurSupport.isBlurDisabled()) return false
        if (!globalBlurEnabled || !shouldTrackFrames()) return false
        return isCrossWindowBlurActive() || autoDiscoverSource() != null
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (enabled && !wasEnabled) {
            view.viewRootImpl?.notifyRendererForGpuLoadUp("axBlur")
        }
        if (!enabled) {
            clear()
        } else {
            updatePreDrawObserver()
        }
        updateSettingsObserver()
        if (enabled) {
            applyBlurSettings()
        }
        invalidateHost()
        wasEnabled = enabled
    }

    fun onVisibilityAggregated(isVisible: Boolean) {
        if (visible == isVisible) return
        visible = isVisible
        if (isVisible) {
            updatePreDrawObserver()
        } else {
            clear()
        }
    }

    fun setAlpha(alpha: Int) {
        val coerced = alpha.coerceIn(0, 255)
        if (this.alpha == coerced) return
        val previous = this.alpha
        this.alpha = coerced
        drawableAlphaStates.entries.forEach { entry ->
            if (entry.value.alpha == previous) {
                entry.setValue(entry.value.copy(alpha = coerced))
            }
        }
        updateDrawableAlphas()
        invalidateHost()
    }

    fun setOverlayColor(color: Int) {
        if (overlayColor == color) return
        overlayColor = color
        invalidateHost()
    }

    fun setBlurRadiusPx(radius: Float) {
        useSettingsBlurRadius = false
        baseBlurRadiusPx = radius
        updateBlurRadiusPx(radius * blurFraction)
        updateSettingsObserver()
    }

    fun setBlurFraction(fraction: Float) {
        var f = fraction.coerceIn(0f, 1f)
        if (abs(blurFraction - f) < 0.01f) return
        blurFraction = f
        updateBlurRadiusPx(baseBlurRadiusPx * f)
    }

    fun setFadeAngleDeg(angleDeg: Float) {
        fadeAngleRad = (angleDeg % 360f) * (PI.toFloat() / 180f)
        invalidateHost()
    }

    fun setSaturation(saturation: Float) {
        sourceBlurSaturation = saturation.coerceIn(0f, 2f)
        sourceBlurEffect = null
        invalidateHost()
    }

    fun setColorCurve(curveBias: Float, minX: Float, maxX: Float, minY: Float, maxY: Float) {
        sourceBlurCurveBias = curveBias
        sourceBlurMinX = minX
        sourceBlurMaxX = maxX
        sourceBlurMinY = minY
        sourceBlurMaxY = maxY
        sourceBlurEffect = null
        invalidateHost()
    }

    fun setCaptureScale(scale: Float) {
        captureScale = scale.coerceIn(0.1f, 1f)
    }

    fun setBackdropFrozen(frozen: Boolean) {
        if (isBackdropFrozen == frozen) return
        isBackdropFrozen = frozen
        if (!frozen) {
            sourceBlurDirty = true
        }
    }

    fun isBackdropFrozen(): Boolean = isBackdropFrozen

    fun invalidateBackdrop() {
        isBackdropFrozen = false
        sourceBlurDirty = true
        invalidateHost()
    }

    private fun setSourceViewInternal(source: View?) {
        if (sourceView === source) return
        val previous = sourceView
        sourceView = source
        sourceDrawStopBranch = null
        if (previous != null && previous !== view) {
            trackedStates.remove(previous)
        }
        if (source != null) {
            trackView(source)
        }
        discardSourceBlur()
        updatePreDrawObserver()
        invalidateHost()
    }

    fun setCrossWindowAlphaSource(source: View?) {
        if (crossWindowAlphaSource === source) return
        crossWindowAlphaSource = source
        updateDrawableAlphas()
        invalidateHost()
    }

    fun setFadeRange(fadeTop: Float, fadeBottom: Float) {
        var top = fadeTop.coerceIn(0f, 1f)
        var bottom = fadeBottom.coerceIn(0f, 1f)
        if (abs(lastFadeTop - top) < 0.01f && abs(lastFadeBottom - bottom) < 0.01f && lastFadeTop >= 0f) return
        lastFadeTop = top
        lastFadeBottom = bottom
        if (fadeTopRatio != top || fadeBottomRatio != bottom) {
            fadeTopRatio = top
            fadeBottomRatio = bottom
            fadeAngleRad = 0f
            invalidateHost()
        }
    }

    fun useSettings(settingsSpec: AxBackdropBlurSettingsSpec) {
        useSettingsBlurRadius = true
        settingsInteractor = AxBackdropBlurInteractor(view.context, settingsSpec)
        applyBlurSettings()
        resetSettingsObserver()
    }

    private fun onAttachedToWindow() {
        if (attached) return
        attached = true
        applyBlurSettings()
        updateSettingsObserver()
        updatePreDrawObserver()
    }

    private fun onDetachedFromWindow() {
        if (!attached) return
        attached = false
        stopSettingsObserver()
        clear()
    }

    fun dispose() {
        view.removeOnAttachStateChangeListener(attachListener)
        onDetachedFromWindow()
    }

    fun verifyDrawable(who: Drawable): Boolean {
        return drawables.values.any { it === who }
    }

    fun dump(pw: PrintWriter) {
        pw.println("BlurEngine:")
        pw.println("  view=${describeView(view)}")
        pw.println("  enabled=$enabled attached=$attached globalBlurEnabled=$globalBlurEnabled")
        pw.println("  blurRadiusPx=$blurRadiusPx baseBlurRadiusPx=$baseBlurRadiusPx " +
            "blurFraction=$blurFraction useSettingsBlurRadius=$useSettingsBlurRadius")
        pw.println("  alpha=$alpha overlayColor=${Integer.toHexString(overlayColor)}")
        pw.println("  crossWindowBlurSupported=${AxBlurSupport.supportsCrossWindowBlur()} " +
            "crossWindowBlurActive=${isCrossWindowBlurActive()}")
        pw.println("  crossWindowAlphaSource=${describeView(crossWindowAlphaSource)}")
        pw.println("  sourceView=${describeView(sourceView)}")
        pw.println("  sourceBlurRecorded=$sourceBlurRecorded sourceBlurDirty=$sourceBlurDirty " +
            "recordedSourceState=$recordedSourceState")
        pw.println("  sourceBlurEffectRadius=$sourceBlurEffectRadius sourceBlurSaturation=" +
            "$sourceBlurSaturation sourceBlurCurveBias=$sourceBlurCurveBias captureScale=$captureScale")
        pw.println("  sourceBlurNodeHasDisplayList=${sourceBlurNode.hasDisplayList()}")
        pw.println("  sourceDrawStopBranch=${describeView(sourceDrawStopBranch)}")
        pw.println("  sourceContentViews=${sourceContentViews.size}")
        sourceContentViews.forEach { pw.println("    ${describeView(it)}") }
        pw.println("  trackedStates=${trackedStates.size} observingPreDraw=$observingPreDraw " +
            "observingDraw=$observingDraw isBackdropFrozen=$isBackdropFrozen")
        trackedStates.keys.forEach { pw.println("    ${describeView(it)}") }
        pw.println("  drawables=${drawables.size}")
        drawables.forEach { (key, drawable) ->
            pw.println("    key=${System.identityHashCode(key)} bounds=${drawable.bounds} " +
                "requestedState=${drawableAlphaStates[key]} " +
                "resolvedAlpha=${resolvedDrawableAlphas[key]} state=$drawable")
        }
        pw.println("  lastDrawBounds=$lastDrawBounds lastDrawHardwareAccelerated=" +
            "$lastDrawHardwareAccelerated lastDrawPath=$lastDrawPath " +
            "lastDrawSucceeded=$lastDrawSucceeded")
        pw.println("  fadeTopRatio=$fadeTopRatio fadeBottomRatio=$fadeBottomRatio " +
            "fadeAngleRad=$fadeAngleRad")
        pw.println("  windowAlpha=${view.viewRootImpl?.mWindowAttributes?.alpha ?: Float.NaN} " +
            "windowVisibility=${view.windowVisibility} hasWindowFocus=${view.hasWindowFocus()} " +
            "shown=${view.isShown} rootView=${describeView(view.rootView)}")
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        target: View,
        alpha: Int = this.alpha,
    ): Boolean {
        trackView(target)
        if (target.width <= 0 || target.height <= 0) {
            clearKey(target)
            lastDrawBounds.setEmpty()
            lastDrawHardwareAccelerated = canvas.isHardwareAccelerated
            lastDrawPath = "target_geometry_unavailable"
            lastDrawSucceeded = false
            return false
        }
        val bounds = targetBounds(target)
        val scale = targetCornerScale(target, bounds)
        val background = target.background
        if (background is GradientDrawable) {
            val radii = background.cornerRadii
            if (radii != null && radii.size >= 8) {
                val drawRadii = scaledCornerRadii(radii, scale)
                return drawInternal(
                    canvas,
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    drawRadii,
                    drawRadii.maxCornerRadius(),
                    alpha,
                    target,
                    visibilitySource = target,
                )
            }
            return drawInternal(
                canvas,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                null,
                background.cornerRadius * scale,
                alpha,
                target,
                visibilitySource = target,
            )
        }
        return drawInternal(
            canvas,
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            null,
            0f,
            alpha,
            target,
            visibilitySource = target,
        )
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        bounds: Rect?,
        cornerRadii: FloatArray,
        alpha: Int = this.alpha,
    ): Boolean {
        if (bounds == null) {
            lastDrawBounds.setEmpty()
            lastDrawHardwareAccelerated = canvas.isHardwareAccelerated
            lastDrawPath = "null_bounds"
            lastDrawSucceeded = false
            return false
        }
        return draw(canvas, bounds.left, bounds.top, bounds.right, bounds.bottom, cornerRadii, alpha)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        bounds: RectF?,
        clipPath: Path?,
        cornerRadius: Float,
        alpha: Int = this.alpha,
    ): Boolean {
        if (bounds == null) {
            lastDrawBounds.setEmpty()
            lastDrawHardwareAccelerated = canvas.isHardwareAccelerated
            lastDrawPath = "null_bounds"
            lastDrawSucceeded = false
            return false
        }
        bounds.roundOut(targetRect)
        return drawInternal(
            canvas,
            targetRect.left,
            targetRect.top,
            targetRect.right,
            targetRect.bottom,
            null,
            cornerRadius,
            alpha,
            defaultKey,
            clipPath,
        )
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray,
        alpha: Int = this.alpha,
    ): Boolean {
        val cornerRadius = if (cornerRadii.size >= 8) {
            cornerRadii.maxCornerRadius()
        } else {
            0f
        }
        return drawInternal(canvas, left, top, right, bottom, cornerRadii, cornerRadius, alpha)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadius: Float,
        alpha: Int = this.alpha,
    ): Boolean {
        return drawInternal(canvas, left, top, right, bottom, null, cornerRadius, alpha)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        key: Any,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadius: Float,
        alpha: Int = this.alpha,
    ): Boolean {
        return drawInternal(canvas, left, top, right, bottom, null, cornerRadius, alpha, key)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        key: Any,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray,
        alpha: Int = this.alpha,
    ): Boolean {
        val cornerRadius = if (cornerRadii.size >= 8) {
            cornerRadii.maxCornerRadius()
        } else {
            0f
        }
        return drawInternal(canvas, left, top, right, bottom, cornerRadii, cornerRadius, alpha, key)
    }

    private fun drawInternal(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        alpha: Int,
        key: Any = defaultKey,
        clipPath: Path? = null,
        visibilitySource: View? = null,
    ): Boolean {
        lastDrawBounds.set(left, top, right, bottom)
        lastDrawHardwareAccelerated = canvas.isHardwareAccelerated
        lastDrawPath = "none"
        lastDrawSucceeded = false
        trackView(view)
        val alphaState = DrawableAlphaState(alpha.coerceIn(0, 255), visibilitySource)
        drawableAlphaStates[key] = alphaState
        val surfaceAlpha = alphaState.alpha
        if (surfaceAlpha <= 0) {
            val drawable = drawables[key]
            if (drawable != null) {
                hideDrawable(drawable)
                resolvedDrawableAlphas[key] = 0
            } else {
                drawableAlphaStates.remove(key)
                resolvedDrawableAlphas.remove(key)
            }
            lastDrawPath = "alpha_zero"
            lastDrawSucceeded = true
            return true
        }
        if (!canDrawGeometry(canvas, left, top, right, bottom)) {
            clearKey(key)
            lastDrawPath = "geometry_unavailable"
            return false
        }
        val crossWindowAlpha = resolvedDrawableAlpha(alphaState)
        if (crossWindowAlpha <= 0) {
            drawables[key]?.let(::hideDrawable)
            resolvedDrawableAlphas[key] = 0
        }
        val drewCrossWindow = if (crossWindowAlpha > 0) {
            drawCrossWindowBlur(
                canvas,
                left,
                top,
                right,
                bottom,
                cornerRadii,
                cornerRadius,
                crossWindowAlpha,
                key,
                clipPath,
            )
        } else {
            false
        }
        autoDiscoverSource()
        val drewSource = drawSourceBlur(
            canvas, left, top, right, bottom,
            cornerRadii, cornerRadius, crossWindowAlpha, clipPath,
        )
        if (!drewSource && !drewCrossWindow) {
            if (crossWindowAlpha > 0) clearKey(key)
            lastDrawPath = "failed"
            return false
        }
        if (!drewCrossWindow) clearKey(key)
        lastDrawPath = when {
            drewSource && drewCrossWindow -> "cross_window_source"
            drewSource -> "source"
            else -> "cross_window"
        }
        lastDrawSucceeded = true
        return true
    }

    private fun drawCrossWindowBlur(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        alpha: Int,
        key: Any,
        clipPath: Path?,
        color: Int = overlayColor,
    ): Boolean {
        if (!AxBlurSupport.supportsCrossWindowBlur()) return false
        val blurDrawable = blurDrawableFor(key) ?: return false
        blurDrawable.setVisible(true, false)
        blurDrawable.setBlurRadius(blurRadiusPx.roundToInt())
        blurDrawable.alpha = alpha
        resolvedDrawableAlphas[key] = alpha
        blurDrawable.setColor(color)
        applyCornerRadius(blurDrawable, cornerRadii, cornerRadius)
        setBoundsIfChanged(blurDrawable, left, top, right, bottom)
        val save = canvas.save()
        clip(canvas, left, top, right, bottom, cornerRadii, cornerRadius, clipPath)
        blurDrawable.draw(canvas)
        applyFadeGradient(canvas, left, top, right, bottom)
        canvas.restoreToCount(save)
        return true
    }

    private fun drawSourceBlur(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        alpha: Int,
        clipPath: Path?,
    ): Boolean {
        val source = sourceView ?: return false
        if (
            alpha <= 0 ||
            source === view ||
            view.width <= 0 ||
            view.height <= 0 ||
            source.width <= 0 ||
            source.height <= 0
        ) {
            return false
        }
        if (AxBlurSupport.isBlurDisabled() || viewTreeAlpha(view, includeWindowAlpha = true) <= 0f) {
            if (sourceBlurRecorded) {
                discardSourceBlur()
            }
            return false
        }
        if (shouldRecordSource(source) && !recordSource(source)) return false
        sourceBlurNode.setRenderEffect(resolveSourceBlurEffect())
        val save = if (alpha < 255) {
            canvas.saveLayerAlpha(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat(),
                alpha,
            )
        } else {
            canvas.save()
        }
        clip(canvas, left, top, right, bottom, cornerRadii, cornerRadius, clipPath)
        canvas.drawRenderNode(sourceBlurNode)
        drawOverlay(canvas, left, top, right, bottom, cornerRadii, cornerRadius, clipPath)
        applyFadeGradient(canvas, left, top, right, bottom)
        canvas.restoreToCount(save)
        return true
    }

    private fun shouldRecordSource(source: View): Boolean {
        if (isBackdropFrozen && sourceBlurRecorded) {
            return false
        }
        return sourceBlurDirty ||
            !sourceBlurRecorded ||
            recordedSourceState != sourceRecordFor(source)
    }

    private fun recordSource(source: View): Boolean {
        val nextRecord = sourceRecordFor(source)
        trackView(source)
        clearSourceContentViews()
        val outset = blurRadiusPx.roundToInt().coerceAtLeast(0)
        val nodeWidth = (view.width * captureScale).roundToInt() + outset * 2
        val nodeHeight = (view.height * captureScale).roundToInt() + outset * 2
        sourceBlurNode.setPosition(-outset, -outset, view.width + outset, view.height + outset)
        val recordingCanvas = sourceBlurNode.beginRecording(nodeWidth, nodeHeight)
        val save = recordingCanvas.save()
        recordingCanvas.translate(outset.toFloat(), outset.toFloat())
        updateTransformToView(source)
        recordingCanvas.concat(transformMatrix)
        val recorded = drawSource(recordingCanvas, source)
        recordingCanvas.restoreToCount(save)
        sourceBlurNode.endRecording()
        recordedSourceState = nextRecord
        sourceBlurDirty = !recorded
        sourceBlurRecorded = recorded
        if (!recorded) {
            sourceBlurNode.discardDisplayList()
        }
        return recorded
    }

    private fun drawSource(canvas: Canvas, source: View): Boolean {
        if (source.visibility != View.VISIBLE || source.visualAlpha() <= 0f) return false
        if (source is ViewGroup) {
            if (sourceDrawStopBranch == null) return false
            return drawViewGroupBeforeTarget(canvas, source)
        }
        source.draw(canvas)
        return true
    }

    private fun findSourceBranch(group: ViewGroup, target: View): View? {
        var current = target
        while (current.parent is View) {
            val parent = current.parent as View
            if (parent === group) {
                return current
            }
            current = parent
        }
        return null
    }

    private fun drawViewGroupBeforeTarget(
        canvas: Canvas,
        group: ViewGroup,
    ): Boolean {
        val stopBranch = findSourceBranch(group, view) ?: return false
        val stopIndex = group.indexOfChild(stopBranch)
        if (stopIndex < 0) return false
        var drewContent = drawViewBackground(canvas, group)
        val save = canvas.save()
        canvas.translate(-group.scrollX.toFloat(), -group.scrollY.toFloat())
        if (group.clipChildren) {
            canvas.clipRect(
                group.scrollX,
                group.scrollY,
                group.scrollX + group.width,
                group.scrollY + group.height,
            )
        }
        if (group.clipToPadding) {
            canvas.clipRect(
                group.scrollX + group.paddingLeft,
                group.scrollY + group.paddingTop,
                group.scrollX + group.width - group.paddingRight,
                group.scrollY + group.height - group.paddingBottom,
            )
        }
        for (i in 0 until stopIndex) {
            drewContent = drawChild(canvas, group.getChildAt(i)) || drewContent
        }
        if (stopBranch is ViewGroup && stopBranch !== view) {
            drewContent = drawNestedViewGroupBeforeTarget(canvas, stopBranch) || drewContent
        }
        canvas.restoreToCount(save)
        return drewContent
    }

    private fun drawViewBackground(canvas: Canvas, source: View): Boolean {
        val background = source.background ?: return false
        if (background.alpha <= 0) return false
        val save = canvas.save()
        canvas.translate(source.scrollX.toFloat(), source.scrollY.toFloat())
        background.draw(canvas)
        canvas.restoreToCount(save)
        return true
    }

    private fun drawNestedViewGroupBeforeTarget(canvas: Canvas, group: ViewGroup): Boolean {
        trackSourceContentView(group)
        val groupAlpha = group.visualAlpha()
        if ((group.visibility != View.VISIBLE && group.animation == null) || groupAlpha <= 0f) {
            return false
        }
        val left = group.left.toFloat()
        val top = group.top.toFloat()
        val matrix = group.matrix
        val save = if (groupAlpha < 1f) {
            childRect.set(0f, 0f, group.width.toFloat(), group.height.toFloat())
            if (!matrix.isIdentity) {
                matrix.mapRect(childRect)
            }
            childRect.offset(left, top)
            canvas.saveLayerAlpha(
                childRect.left,
                childRect.top,
                childRect.right,
                childRect.bottom,
                (groupAlpha * 255).roundToInt().coerceIn(0, 255),
            )
        } else {
            canvas.save()
        }
        canvas.translate(left, top)
        if (!matrix.isIdentity) {
            canvas.concat(matrix)
        }
        if (group.clipChildren) {
            canvas.clipRect(0f, 0f, group.width.toFloat(), group.height.toFloat())
        }
        val drewContent = drawViewGroupBeforeTarget(canvas, group)
        canvas.restoreToCount(save)
        return drewContent
    }

    private fun drawChild(canvas: Canvas, child: View): Boolean {
        trackSourceContentView(child)
        val childAlpha = child.visualAlpha()
        if ((child.visibility != View.VISIBLE && child.animation == null) || childAlpha <= 0f) {
            return false
        }
        if (drawChildRenderNode(canvas, child)) return true
        drawChildFallback(canvas, child, childAlpha)
        return true
    }

    private fun drawChildRenderNode(canvas: Canvas, child: View): Boolean {
        if (!canvas.isHardwareAccelerated || !child.canHaveDisplayList()) return false
        val renderNode = child.updateDisplayListIfDirty()
        if (!renderNode.hasDisplayList()) return false
        canvas.drawRenderNode(renderNode)
        return true
    }

    private fun drawChildFallback(
        canvas: Canvas,
        child: View,
        childAlpha: Float,
    ) {
        val left = child.left.toFloat()
        val top = child.top.toFloat()
        val matrix = child.matrix
        val save = if (childAlpha < 1f) {
            childRect.set(0f, 0f, child.width.toFloat(), child.height.toFloat())
            if (!matrix.isIdentity) {
                matrix.mapRect(childRect)
            }
            childRect.offset(left, top)
            canvas.saveLayerAlpha(
                childRect.left,
                childRect.top,
                childRect.right,
                childRect.bottom,
                (childAlpha * 255).roundToInt().coerceIn(0, 255),
            )
        } else {
            canvas.save()
        }
        canvas.translate(left, top)
        if (!matrix.isIdentity) {
            canvas.concat(matrix)
        }
        child.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun resolveSourceBlurEffect(): RenderEffect {
        val scaledRadius = (blurRadiusPx * captureScale).coerceAtLeast(1f)
        val cached = sourceBlurEffect
        if (cached != null && sourceBlurEffectRadius == scaledRadius) {
            return cached
        }
        val baseEffect = RenderEffect.createBlurEffect(
            scaledRadius,
            scaledRadius,
            Shader.TileMode.CLAMP,
        )
        val cm = ColorMatrix()
        val hasSaturation = sourceBlurSaturation != 1f
        if (hasSaturation) {
            cm.setSaturation(sourceBlurSaturation)
        }

        val hasCurveBias = sourceBlurCurveBias != 0f
        if (hasCurveBias) {
            val contrast = 1f + sourceBlurCurveBias / 100f
            val brightness = (sourceBlurMinY + (sourceBlurMaxY - sourceBlurMinY) * 0.5f) / 128f
            val cm2 = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness * 128f * (1f - contrast) + (sourceBlurMinY - 128f),
                0f, contrast, 0f, 0f, brightness * 128f * (1f - contrast) + (sourceBlurMinY - 128f),
                0f, 0f, contrast, 0f, brightness * 128f * (1f - contrast) + (sourceBlurMinY - 128f),
                0f, 0f, 0f, 1f, 0f,
            ))
            cm.postConcat(cm2)
        }

        val effect = if (hasSaturation || hasCurveBias) {
            RenderEffect.createChainEffect(
                baseEffect,
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm)),
            )
        } else {
            baseEffect
        }
        return effect.also {
            sourceBlurEffect = it
            sourceBlurEffectRadius = scaledRadius
        }
    }

    private fun targetBounds(target: View): Rect {
        targetRectF.set(0f, 0f, target.width.toFloat(), target.height.toFloat())
        if (target !== view) {
            updateTransformToView(target)
            transformMatrix.mapRect(targetRectF)
        }
        targetRectF.roundOut(targetRect)
        return targetRect
    }

    private fun targetCornerScale(target: View, bounds: Rect): Float {
        if (target === view || target.width <= 0 || target.height <= 0) return 1f
        val scaleX = bounds.width().toFloat() / target.width
        val scaleY = bounds.height().toFloat() / target.height
        val scale = max(scaleX, scaleY)
        return if (scale.isFinite() && scale > 0f) scale else 1f
    }

    private fun scaledCornerRadii(radii: FloatArray, scale: Float): FloatArray {
        if (scale == 1f) return radii
        for (index in 0 until 8) {
            scaledCornerRadii[index] = radii[index] * scale
        }
        return scaledCornerRadii
    }

    private fun updateTransformToView(source: View) {
        transformMatrix.reset()
        source.transformMatrixToGlobal(transformMatrix)
        view.transformMatrixToLocal(transformMatrix)
    }

    private fun canDrawGeometry(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean {
        return enabled &&
            !AxBlurSupport.isBlurDisabled() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            blurRadiusPx.roundToInt() > 0 &&
            canvas.isHardwareAccelerated &&
            hasRequiredWindowFocus() &&
            left < right &&
            top < bottom
    }

    private fun hasRequiredWindowFocus(): Boolean {
        return true
    }

    private fun resolvedDrawableAlpha(state: DrawableAlphaState): Int {
        val hostTarget = state.visibilitySource ?: view
        val hostAlpha = viewTreeAlpha(hostTarget, includeWindowAlpha = true)
        if (hostAlpha <= 0f) return 0
        val sourceAlpha = crossWindowAlphaSource?.let { source ->
            if (source === hostTarget || isAncestor(source, hostTarget)) {
                1f
            } else {
                viewTreeAlpha(source, includeWindowAlpha = false)
            }
        } ?: 1f
        val alpha = state.alpha.coerceIn(0, 255).toFloat()
        return (alpha * hostAlpha * sourceAlpha).roundToInt().coerceIn(0, 255)
    }

    private fun viewTreeAlpha(target: View, includeWindowAlpha: Boolean): Float {
        if (!target.isAttachedToWindow || !target.getGlobalVisibleRect(targetRect)) return 0f
        var alpha = 1f
        var current: View? = target
        while (current != null) {
            if (current.visibility != View.VISIBLE) return 0f
            alpha *= current.visualAlpha()
            current = current.parent as? View
        }
        if (includeWindowAlpha) {
            val windowAlpha = view.viewRootImpl?.mWindowAttributes?.alpha ?: 1f
            alpha *= windowAlpha
        }
        return alpha.sanitizedAlphaFactor()
    }

    private fun isAncestor(ancestor: View, target: View): Boolean {
        var current = target.parent as? View
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun updateDrawableAlphas(): Boolean {
        var changed = false
        val iterator = drawables.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val state = drawableAlphaStates[entry.key]
            if (state == null) {
                deactivateCrossWindowBlurDrawable(entry.value)
                resolvedDrawableAlphas.remove(entry.key)
                iterator.remove()
                changed = true
                continue
            }
            val alpha = resolvedDrawableAlpha(state)
            if (alpha <= 0) {
                val currentAlpha = resolvedDrawableAlphas[entry.key] ?: -1
                if (currentAlpha != 0) {
                    hideDrawable(entry.value)
                    resolvedDrawableAlphas[entry.key] = 0
                    changed = true
                }
                continue
            }
            if (resolvedDrawableAlphas[entry.key] != alpha) {
                entry.value.setVisible(true, false)
                entry.value.alpha = alpha
                resolvedDrawableAlphas[entry.key] = alpha
                changed = true
            }
        }
        return changed
    }

    private fun View.visualAlpha(): Float {
        val value = alpha * transitionAlpha
        return value.sanitizedAlphaFactor()
    }

    private fun Float.sanitizedAlphaFactor(): Float {
        return if (isFinite()) coerceIn(0f, 1f) else 0f
    }

    private fun trackView(target: View) {
        if (!shouldTrackFrames()) return
        trackedStates.getOrPut(target) { ViewFrameState() }
            .update(target, transformMatrix, targetRectF, visibleTrackingRect, false)
        updatePreDrawObserver()
    }

    private fun trackSourceContentView(target: View) {
        sourceContentViews.add(target)
        trackView(target)
    }

    private fun shouldTrackFrames(): Boolean {
        return enabled &&
            !AxBlurSupport.isBlurDisabled() &&
            visible &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            blurRadiusPx.roundToInt() > 0
    }

    private fun hasTrackedStateChanged(): Boolean {
        var changed = false
        val iterator = trackedStates.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val target = entry.key
            if (target !== view && !target.isAttachedToWindow) {
                if (target === sourceView || sourceContentViews.remove(target)) {
                    sourceBlurDirty = true
                }
                clearKey(target)
                iterator.remove()
                changed = true
            } else {
                val sourceContentChanged = sourceContentViews.contains(target)
                val affectsSource = target === view || target === sourceView || sourceContentChanged
                val trackSourceDirty = target === sourceView || sourceContentChanged
                val stateChanged = entry.value.update(
                    target,
                    transformMatrix,
                    targetRectF,
                    visibleTrackingRect,
                    trackSourceDirty,
                )
                if (stateChanged && affectsSource) {
                    sourceBlurDirty = true
                }
                changed = changed || stateChanged
            }
        }
        if (trackedStates.isEmpty()) {
            removePreDrawObserver()
        }
        return changed
    }

    private fun updatePreDrawObserver() {
        if (attached && enabled && trackedStates.isNotEmpty()) {
            addPreDrawObserver()
            addDrawObserver()
        } else {
            removePreDrawObserver()
            removeDrawObserver()
        }
    }

    private fun addPreDrawObserver() {
        if (!observingPreDraw) {
            view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            observingPreDraw = true
        }
    }

    private fun removePreDrawObserver() {
        if (observingPreDraw) {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            }
            observingPreDraw = false
        }
    }

    private fun addDrawObserver() {
        if (!observingDraw) {
            view.viewTreeObserver.addOnDrawListener(drawListener)
            observingDraw = true
        }
    }

    private fun removeDrawObserver() {
        if (observingDraw) {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnDrawListener(drawListener)
            }
            observingDraw = false
        }
    }

    private fun blurDrawableFor(key: Any): BackgroundBlurDrawable? {
        return drawables[key] ?: view.viewRootImpl
            ?.createBackgroundBlurDrawable()
            ?.also { drawables[key] = it }
    }

    private fun setBoundsIfChanged(
        drawable: BackgroundBlurDrawable,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val bounds = drawable.bounds
        if (
            bounds.left != left ||
                bounds.top != top ||
                bounds.right != right ||
                bounds.bottom != bottom
        ) {
            drawable.setBounds(left, top, right, bottom)
        }
    }

    private fun applyCornerRadius(
        blurDrawable: BackgroundBlurDrawable,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
    ) {
        if (cornerRadii != null && cornerRadii.size >= 8) {
            blurDrawable.setCornerRadius(
                cornerRadii.cornerRadiusAt(0),
                cornerRadii.cornerRadiusAt(2),
                cornerRadii.cornerRadiusAt(6),
                cornerRadii.cornerRadiusAt(4),
            )
            return
        }
        blurDrawable.setCornerRadius(cornerRadius.coerceAtLeast(0f))
    }

    private fun clip(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        clipPath: Path?,
    ) {
        if (clipPath != null) {
            canvas.clipPath(clipPath)
            return
        }
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        path.rewind()
        if (cornerRadii != null && cornerRadii.size >= 8) {
            path.addRoundRect(rect, cornerRadii, Path.Direction.CW)
            canvas.clipPath(path)
        } else if (cornerRadius > 0f) {
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.clipPath(path)
        }
    }

    private fun drawOverlay(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        clipPath: Path?,
        alpha: Int = 255,
    ) {
        if (Color.alpha(overlayColor) == 0) return
        overlayPaint.alpha = (Color.alpha(overlayColor) * alpha / 255f)
            .roundToInt()
            .coerceIn(0, 255)

        val useGradient = overlayColor and 0xFF000000.toInt() == 0x01000000.toInt()
        if (useGradient) {
            overlayPaint.shader = LinearGradient(
                left.toFloat(), top.toFloat(),
                right.toFloat(), bottom.toFloat(),
                intArrayOf(overlayColor, overlayColor and 0x00FFFFFF or (0x80 shl 24)),
                null, Shader.TileMode.CLAMP,
            )
        } else {
            overlayPaint.shader = null
        }
        overlayPaint.color = overlayColor

        if (clipPath != null) {
            canvas.drawPath(clipPath, overlayPaint)
            overlayPaint.shader = null
            return
        }
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        path.rewind()
        if (cornerRadii != null && cornerRadii.size >= 8) {
            path.addRoundRect(rect, cornerRadii, Path.Direction.CW)
            canvas.drawPath(path, overlayPaint)
        } else if (cornerRadius > 0f) {
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.drawPath(path, overlayPaint)
        } else {
            canvas.drawRect(rect, overlayPaint)
        }
        if (useGradient) overlayPaint.shader = null
    }

    private fun applyFadeGradient(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int) {
        if (fadeTopRatio <= 0f && fadeBottomRatio >= 1f) return
        if (right <= left || bottom <= top) return

        val w = (right - left).toFloat()
        val h = (bottom - top).toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = left + w / 2f
        val cy = top + h / 2f
        val len = sqrt(w * w + h * h)
        val dx = (len / 2f) * cos(fadeAngleRad)
        val dy = (len / 2f) * sin(fadeAngleRad)

        val positions = floatArrayOf(0f, fadeTopRatio, fadeBottomRatio, 1f)
        if (positions[1] >= positions[2]) return
        val colors = intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT)

        fadeGradientPaint.shader = LinearGradient(
            cx - dx, cy - dy, cx + dx, cy + dy,
            colors, positions, Shader.TileMode.CLAMP
        )
        fadeGradientPaint.blendMode = BlendMode.DST_IN

        val saveCount = canvas.saveLayer(null, fadeGradientPaint)
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), fadeGradientPaint)
        canvas.restoreToCount(saveCount)
    }

    private fun applyBlurSettings() {
        if (useSettingsBlurRadius) {
            globalBlurEnabled = settingsInteractor.settings().enabled
            baseBlurRadiusPx = settingsInteractor.settings().blurRadiusPx
            updateBlurRadiusPx(baseBlurRadiusPx * blurFraction)
        }
    }

    private fun updateBlurRadiusPx(radius: Float) {
        var coerced = if (radius.isFinite()) radius.coerceAtLeast(0f) else 0f
        if (abs(lastBlurRadius - coerced) < 1f && lastBlurRadius >= 0f) return
        lastBlurRadius = coerced
        if (blurRadiusPx == coerced) return
        blurRadiusPx = coerced
        sourceBlurEffect = null
        if (coerced == 0f) {
            clear()
            return
        }
        if (blurFraction > 0f && blurFraction < 1f && frameTracker.isLimited(100L)) {
            discardSourceBlur()
        }
        invalidateHost()
    }

    private fun updateSettingsObserver() {
        if (observeSettings && attached && enabled && useSettingsBlurRadius) {
            startSettingsObserver()
        } else {
            stopSettingsObserver()
        }
    }

    private fun resetSettingsObserver() {
        settingsSubscription?.stop()
        settingsSubscription = null
        updateSettingsObserver()
    }

    private fun startSettingsObserver() {
        val observer = settingsSubscription ?: settingsInteractor.createSubscription {
            applyBlurSettings()
        }.also {
            settingsSubscription = it
        }
        observer.start()
    }

    private fun stopSettingsObserver() {
        settingsSubscription?.stop()
    }

    fun clear() {
        clearCrossWindowBlur()
        trackedStates.clear()
        sourceView = null
        sourceDrawStopBranch = null
        discardSourceBlur()
        updatePreDrawObserver()
    }

    private fun clearCrossWindowBlur() {
        drawables.values.forEach(::deactivateCrossWindowBlurDrawable)
        drawables.clear()
        drawableAlphaStates.clear()
        resolvedDrawableAlphas.clear()
        updatePreDrawObserver()
        invalidateHost()
    }

    private fun invalidateHost() {
        view.postInvalidateOnAnimation()
    }

    private fun clearKey(key: Any) {
        drawableAlphaStates.remove(key)
        resolvedDrawableAlphas.remove(key)
        drawables.remove(key)?.let(::deactivateCrossWindowBlurDrawable)
    }

    private fun deactivateCrossWindowBlurDrawable(drawable: BackgroundBlurDrawable) {
        drawable.alpha = 0
        drawable.setColor(Color.TRANSPARENT)
        drawable.setBounds(0, 0, 0, 0)
        drawable.setVisible(false, false)
        drawable.setBlurRadius(0)
    }

    private fun hideDrawable(drawable: BackgroundBlurDrawable) {
        if (drawable.alpha != 0 || drawable.isVisible) {
            drawable.setVisible(false, false)
            drawable.alpha = 0
        }
    }

    private fun discardSourceBlur() {
        clearSourceContentViews()
        sourceBlurNode.discardDisplayList()
        sourceBlurRecorded = false
        sourceBlurDirty = true
        isBackdropFrozen = false
        recordedSourceState = SourceRecord()
    }

    private fun sourceRecordFor(source: View): SourceRecord {
        return SourceRecord(
            viewWidth = view.width,
            viewHeight = view.height,
            sourceWidth = source.width,
            sourceHeight = source.height,
        )
    }

    private fun clearSourceContentViews() {
        sourceContentViews.forEach { trackedStates.remove(it) }
        sourceContentViews.clear()
    }

    private fun FloatArray.cornerRadiusAt(index: Int): Float {
        return (this[index] + this[index + 1]) * 0.5f
    }

    private fun FloatArray.maxCornerRadius(): Float {
        var radius = 0f
        for (index in 0..6 step 2) {
            radius = max(radius, cornerRadiusAt(index))
        }
        return radius
    }

    private data class DrawableAlphaState(
        val alpha: Int,
        val visibilitySource: View?,
    )

    private data class SourceRecord(
        val viewWidth: Int = -1,
        val viewHeight: Int = -1,
        val sourceWidth: Int = -1,
        val sourceHeight: Int = -1,
    )

    private inner class ViewFrameState {
        private var metrics = ViewMetrics()

        fun update(
            target: View,
            matrix: Matrix,
            rect: RectF,
            visibleRect: Rect,
            trackDirty: Boolean,
        ): Boolean {
            rect.set(0f, 0f, target.width.toFloat(), target.height.toFloat())
            matrix.reset()
            target.transformMatrixToGlobal(matrix)
            matrix.mapRect(rect)
            val targetVisibleInWindow = target.getGlobalVisibleRect(visibleRect)
            val targetClipSet = target.getClipBounds(visibleRect)
            val nextMetrics = ViewMetrics(
                width = target.width,
                height = target.height,
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
                scrollX = target.scrollX,
                scrollY = target.scrollY,
                childCount = if (target is ViewGroup) target.childCount else -1,
                visibleInWindow = targetVisibleInWindow,
                visLeft = if (targetVisibleInWindow) visibleRect.left else Int.MIN_VALUE,
                visTop = if (targetVisibleInWindow) visibleRect.top else Int.MIN_VALUE,
                visRight = if (targetVisibleInWindow) visibleRect.right else Int.MIN_VALUE,
                visBottom = if (targetVisibleInWindow) visibleRect.bottom else Int.MIN_VALUE,
                alpha = target.visualAlpha(),
                clipIsSet = targetClipSet,
                clipLeft = if (targetClipSet) visibleRect.left else Int.MIN_VALUE,
                clipTop = if (targetClipSet) visibleRect.top else Int.MIN_VALUE,
                clipRight = if (targetClipSet) visibleRect.right else Int.MIN_VALUE,
                clipBottom = if (targetClipSet) visibleRect.bottom else Int.MIN_VALUE,
            )
            val changed = metrics != nextMetrics
            if (changed) {
                metrics = nextMetrics
            }
            return changed
        }
    }

    private data class ViewMetrics(
        val width: Int = -1,
        val height: Int = -1,
        val left: Float = Float.NaN,
        val top: Float = Float.NaN,
        val right: Float = Float.NaN,
        val bottom: Float = Float.NaN,
        val scrollX: Int = Int.MIN_VALUE,
        val scrollY: Int = Int.MIN_VALUE,
        val childCount: Int = -1,
        val visibleInWindow: Boolean = false,
        val visLeft: Int = Int.MIN_VALUE,
        val visTop: Int = Int.MIN_VALUE,
        val visRight: Int = Int.MIN_VALUE,
        val visBottom: Int = Int.MIN_VALUE,
        val alpha: Float = Float.NaN,
        val clipIsSet: Boolean = false,
        val clipLeft: Int = Int.MIN_VALUE,
        val clipTop: Int = Int.MIN_VALUE,
        val clipRight: Int = Int.MIN_VALUE,
        val clipBottom: Int = Int.MIN_VALUE,
    )

    private fun autoDiscoverSource(): View? {
        if (!view.isAttachedToWindow) return null
        val root = view.rootView as? ViewGroup ?: return null
        val branch = findSourceBranch(root, view) ?: return null
        if (sourceView !== root) {
            setSourceViewInternal(root)
        }
        if (sourceDrawStopBranch !== branch) {
            sourceDrawStopBranch = branch
            discardSourceBlur()
        }
        return root
    }

    private fun describeView(target: View?): String {
        if (target == null) return "null"
        return target.javaClass.simpleName + "@" + System.identityHashCode(target) +
            "{size=" + target.width + "x" + target.height +
            ",attached=" + target.isAttachedToWindow +
            ",visibility=" + target.visibility +
            ",alpha=" + target.alpha +
            ",transitionAlpha=" + target.transitionAlpha + "}"
    }
}
