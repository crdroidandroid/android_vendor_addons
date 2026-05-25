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

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import com.android.axion.blur.settings.AxBackdropBlurInteractor
import com.android.axion.blur.settings.AxBackdropBlurSettingsModel
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import kotlin.math.roundToInt

class AxWindowBlurController @JvmOverloads constructor(
    private val window: Window,
    context: Context? = null,
    private val blurBehind: Boolean = false,
    private val blurBehindRadiusScale: Float = DEFAULT_BLUR_BEHIND_RADIUS_SCALE,
    private val surfaceAlpha: Int = DEFAULT_SURFACE_ALPHA,
) {
    private val context = context ?: window.context
    private val blurInteractor = AxBackdropBlurInteractor(this.context)
    private val settingsObserver = blurInteractor.createSubscription {
        apply()
    }
    private val surfaces = LinkedHashMap<View, SurfaceBlurState>()
    private val pendingPreDrawListeners = ArrayList<PendingPreDraw>()
    private var started = false

    fun start() {
        if (!started) {
            settingsObserver.start()
            started = true
        }
        apply()
    }

    fun stop() {
        if (started) {
            settingsObserver.stop()
            started = false
        }
        clearPendingPreDrawListeners()
        clearWindowBlur()
        restoreSurfaces()
    }

    fun apply() {
        apply(blurInteractor.settings())
    }

    fun addSurface(view: View?) {
        if (view == null || surfaces.containsKey(view)) return
        surfaces[view] = SurfaceBlurState.capture(view)
        if (started) apply()
    }

    fun applyOnNextDraw(trigger: View?) {
        if (trigger == null) return
        val observer = trigger.viewTreeObserver
        if (!observer.isAlive) return
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                removePendingPreDrawListener(trigger, observer, this)
                apply()
                return true
            }
        }
        pendingPreDrawListeners.add(PendingPreDraw(trigger, observer, listener))
        observer.addOnPreDrawListener(listener)
    }

    fun detach() {
        stop()
        surfaces.clear()
    }

    private fun apply(settings: AxBackdropBlurSettingsModel) {
        val radius = settings.blurRadiusPx.roundToInt()
        val supportedRadius = if (AxBlurSupport.supportsCrossWindowBlur()) radius else 0
        val alpha = if (supportedRadius > 0) surfaceAlpha.sanitizedAlpha() else 255
        clearWindowBlur()
        applyWindowBlur(supportedRadius)
        applySurfaceBlur(supportedRadius, alpha)
    }

    private fun applyWindowBlur(radius: Int) {
        if (!blurBehind || radius <= 0) return
        setWindowBackgroundBlurRadius(radius)
        val attrs = window.attributes
        attrs.setBlurBehindRadius(
            (radius * blurBehindRadiusScale).roundToInt().coerceAtLeast(0),
        )
        attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        window.attributes = attrs
    }

    private fun clearWindowBlur() {
        setWindowBackgroundBlurRadius(0)
        val attrs = window.attributes
        attrs.setBlurBehindRadius(0)
        attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        window.attributes = attrs
    }

    private fun setWindowBackgroundBlurRadius(radius: Int) {
        if (window.peekDecorView() != null) {
            window.setBackgroundBlurRadius(radius)
        }
    }

    private fun applySurfaceBlur(radius: Int, alpha: Int) {
        surfaces.forEach { (view, state) ->
            state.apply(view, context, radius, alpha)
        }
    }

    private fun restoreSurfaces() {
        surfaces.forEach { (view, state) -> state.restore(view) }
    }

    private fun clearPendingPreDrawListeners() {
        pendingPreDrawListeners.toList().forEach {
            removePendingPreDrawListener(it.view, it.observer, it.listener)
        }
    }

    private fun removePendingPreDrawListener(
        view: View,
        observer: ViewTreeObserver,
        listener: ViewTreeObserver.OnPreDrawListener,
    ) {
        pendingPreDrawListeners.removeAll { it.listener === listener }
        val currentObserver = view.viewTreeObserver
        if (currentObserver.isAlive) {
            currentObserver.removeOnPreDrawListener(listener)
        } else if (observer.isAlive) {
            observer.removeOnPreDrawListener(listener)
        }
    }

    private fun Int.sanitizedAlpha(): Int {
        return coerceIn(0, 255)
    }

    private class SurfaceBlurState private constructor(
        private var originalBackground: Drawable?,
        private var originalAlpha: Int,
    ) {
        private var blurDrawable: BackgroundBlurDrawable? = null
        private var layerDrawable: LayerDrawable? = null
        private var surfaceBackground: Drawable? = originalBackground?.mutate()
        private val outline = Outline()

        fun apply(view: View, context: Context, radius: Int, alpha: Int) {
            captureExternalBackground(view)
            if (radius <= 0) {
                restore(view)
                return
            }
            val existingBlur = surfaceBackground as? BackgroundBlurDrawable
            if (existingBlur != null) {
                existingBlur.setVisible(true, false)
                existingBlur.setBlurRadius(radius)
                existingBlur.setAlpha(255)
                view.background = existingBlur
                return
            }
            val blur = blurDrawable ?: view.viewRootImpl?.createBackgroundBlurDrawable()?.also {
                blurDrawable = it
            } ?: run {
                surfaceBackground.applyAlpha(alpha)
                return
            }
            blur.setVisible(true, false)
            blur.setAlpha(255)
            blur.setBlurRadius(radius)
            blur.setColor(
                if (surfaceBackground == null) {
                    AxBlurColors.tint(context)
                } else {
                    Color.TRANSPARENT
                },
            )
            applyCorners(blur)
            val overlay = surfaceBackground
            if (overlay == null) {
                view.background = blur
            } else {
                overlay.applyAlpha(alpha)
                view.background = layerDrawable ?: LayerDrawable(arrayOf(blur, overlay)).also {
                    layerDrawable = it
                }
            }
        }

        fun restore(view: View) {
            blurDrawable?.setVisible(false, false)
            blurDrawable?.setBlurRadius(0)
            surfaceBackground.applyAlpha(originalAlpha)
            if (view.background === layerDrawable || view.background === blurDrawable) {
                view.background = surfaceBackground
            }
            layerDrawable = null
        }

        private fun captureExternalBackground(view: View) {
            val background = view.background
            if (
                background !== layerDrawable &&
                background !== blurDrawable &&
                background !== surfaceBackground
            ) {
                originalBackground = background
                originalAlpha = background?.alpha ?: 255
                surfaceBackground = background?.mutate()
                layerDrawable = null
            }
        }

        private fun applyCorners(blur: BackgroundBlurDrawable) {
            val background = surfaceBackground
            if (background is GradientDrawable) {
                val radii = background.cornerRadii
                if (radii != null && radii.size >= 8) {
                    blur.setCornerRadius(
                        radii.cornerRadiusAt(0),
                        radii.cornerRadiusAt(2),
                        radii.cornerRadiusAt(6),
                        radii.cornerRadiusAt(4),
                    )
                    return
                }
                blur.setCornerRadius(background.cornerRadius)
                return
            }
            if (background != null) {
                outline.setEmpty()
                background.getOutline(outline)
                val radius = outline.radius.takeIf { it >= 0f } ?: 0f
                blur.setCornerRadius(radius)
                return
            }
            blur.setCornerRadius(0f)
        }

        private fun Drawable?.applyAlpha(alpha: Int) {
            this?.alpha = alpha
        }

        private fun FloatArray.cornerRadiusAt(index: Int): Float {
            return (this[index] + this[index + 1]) * 0.5f
        }

        companion object {
            fun capture(view: View): SurfaceBlurState {
                val background = view.background
                return SurfaceBlurState(background, background?.alpha ?: 255)
            }
        }
    }

    companion object {
        private const val DEFAULT_BLUR_BEHIND_RADIUS_SCALE = 0.55f
        private const val DEFAULT_SURFACE_ALPHA = AX_BLUR_SURFACE_ALPHA

        @JvmStatic
        fun supportsBlur(): Boolean {
            return AxBlurSupport.supportsCrossWindowBlur()
        }

        @JvmStatic
        @JvmOverloads
        fun applyBlurBehind(window: Window?, context: Context? = null) {
            apply(window, context, true, 1f)
        }

        @JvmStatic
        @JvmOverloads
        fun apply(
            window: Window?,
            context: Context? = null,
            blurBehind: Boolean = false,
            blurBehindRadiusScale: Float = DEFAULT_BLUR_BEHIND_RADIUS_SCALE,
            surfaceAlpha: Int = DEFAULT_SURFACE_ALPHA,
        ) {
            if (window == null) return
            AxWindowBlurController(
                window,
                context,
                blurBehind,
                blurBehindRadiusScale,
                surfaceAlpha,
            ).apply()
        }
    }

    private data class PendingPreDraw(
        val view: View,
        val observer: ViewTreeObserver,
        val listener: ViewTreeObserver.OnPreDrawListener,
    )
}
