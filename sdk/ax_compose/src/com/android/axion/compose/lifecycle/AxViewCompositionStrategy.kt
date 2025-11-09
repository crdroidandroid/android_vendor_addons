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
package com.android.axion.compose.lifecycle

import android.util.Log
import android.view.View
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

object AxViewCompositionStrategy : ViewCompositionStrategy {

    private const val TAG = "AxViewCompositionStrategy"

    override fun installFor(view: AbstractComposeView): () -> Unit {
        var currentLifecycleObserver: LifecycleEventObserver? = null
        var currentLifecycle: Lifecycle? = null

        fun clearLifecycleObserver() {
            currentLifecycleObserver?.let { observer ->
                currentLifecycle?.removeObserver(observer)
            }
            currentLifecycleObserver = null
            currentLifecycle = null
        }

        fun tryBindLifecycle(v: View) {
            clearLifecycleObserver()
            val lifecycleOwner = v.findViewTreeLifecycleOwner()
            if (lifecycleOwner != null) {
                val lifecycle = lifecycleOwner.lifecycle
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        view.disposeComposition()
                    }
                }
                lifecycle.addObserver(observer)
                currentLifecycle = lifecycle
                currentLifecycleObserver = observer
            } else {
                Log.w(TAG, "No ViewTreeLifecycleOwner for $view, " +
                    "will dispose on detach instead")
            }
        }

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                tryBindLifecycle(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                clearLifecycleObserver()
                view.disposeComposition()
            }
        }

        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) {
            tryBindLifecycle(view)
        }

        return {
            clearLifecycleObserver()
            view.removeOnAttachStateChangeListener(listener)
        }
    }
}
