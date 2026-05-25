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

import android.R as AndroidR
import android.app.Dialog
import android.view.View
import android.view.ViewGroup

object AxWindowBlurHooks {
    @JvmStatic
    fun start(dialog: Dialog) {
        start(dialog, false)
    }

    @JvmStatic
    fun start(dialog: Dialog, surfaceId: Int) {
        start(dialog, surfaceId, false)
    }

    @JvmStatic
    fun start(dialog: Dialog, surfaceId: Int, blurBehind: Boolean) {
        addSurface(dialog, dialog.findViewById(surfaceId), blurBehind)
        controller(dialog, blurBehind)?.start()
    }

    @JvmStatic
    fun start(dialog: Dialog, surface: View?, blurBehind: Boolean) {
        addSurface(dialog, surface, blurBehind)
        controller(dialog, blurBehind)?.start()
    }

    @JvmStatic
    fun start(dialog: Dialog, blurBehind: Boolean) {
        addContentSurfaces(dialog, blurBehind)
        controller(dialog, blurBehind)?.start()
    }

    @JvmStatic
    fun stop(dialog: Dialog) {
        controllerOrNull(dialog)?.stop()
    }

    @JvmStatic
    fun detach(dialog: Dialog) {
        val decorView = dialog.window?.decorView ?: return
        controllerOrNull(dialog)?.detach()
        decorView.setTag(R.id.ax_window_blur_controller, null)
    }

    @JvmStatic
    fun apply(dialog: Dialog) {
        controller(dialog, false)?.apply()
    }

    @JvmStatic
    fun addSurface(dialog: Dialog, view: View?) {
        addSurface(dialog, view, false)
    }

    @JvmStatic
    fun addSurface(dialog: Dialog, view: View?, blurBehind: Boolean) {
        controller(dialog, blurBehind)?.addSurface(view)
    }

    @JvmStatic
    fun addContentSurfaces(dialog: Dialog) {
        addContentSurfaces(dialog, false)
    }

    @JvmStatic
    fun addContentSurfaces(dialog: Dialog, blurBehind: Boolean) {
        dialog.findViewById<ViewGroup>(AndroidR.id.content)?.let { content ->
            for (index in 0 until content.childCount) {
                val child = content.getChildAt(index)
                if (shouldAddContentSurface(dialog, child)) {
                    addSurface(dialog, child, blurBehind)
                }
            }
        }
    }

    @JvmStatic
    fun applyOnNextDraw(dialog: Dialog, trigger: View?) {
        applyOnNextDraw(dialog, trigger, false)
    }

    @JvmStatic
    fun applyOnNextDraw(dialog: Dialog, trigger: View?, blurBehind: Boolean) {
        controller(dialog, blurBehind)?.applyOnNextDraw(trigger)
    }

    @JvmStatic
    fun attach(dialog: Dialog, surface: View?) {
        attach(dialog, surface, false)
    }

    @JvmStatic
    fun attach(dialog: Dialog, surface: View?, blurBehind: Boolean) {
        addSurface(dialog, surface, blurBehind)
        if (dialog.window?.decorView?.isAttachedToWindow == true) {
            controller(dialog, blurBehind)?.start()
        }
    }

    private fun shouldAddContentSurface(dialog: Dialog, view: View): Boolean {
        if (view.background != null) return true
        val attrs = dialog.window?.attributes ?: return true
        return attrs.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            attrs.height != ViewGroup.LayoutParams.MATCH_PARENT
    }

    private fun controller(dialog: Dialog, blurBehind: Boolean): AxWindowBlurController? {
        val window = dialog.window ?: return null
        val decorView = window.decorView
        val existing = decorView.getTag(R.id.ax_window_blur_controller)
            as? AxWindowBlurController
        if (existing != null) return existing
        val controller = AxWindowBlurController(window, dialog.context, blurBehind)
        decorView.setTag(R.id.ax_window_blur_controller, controller)
        decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                controller.start()
            }

            override fun onViewDetachedFromWindow(view: View) {
                controller.detach()
                view.setTag(R.id.ax_window_blur_controller, null)
                view.removeOnAttachStateChangeListener(this)
            }
        })
        return controller
    }

    private fun controllerOrNull(dialog: Dialog): AxWindowBlurController? {
        return dialog.window?.decorView?.getTag(R.id.ax_window_blur_controller)
            as? AxWindowBlurController
    }
}
