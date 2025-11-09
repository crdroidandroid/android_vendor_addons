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

import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * Initializes Compose for a root [View] outside of an Activity - e.g. a window
 * added via [android.view.WindowManager.addView], a service overlay, or any
 * non-Activity context.
 *
 * Unlike the platform's ComposeInitializer which only wires a basic lifecycle and
 * saved state, this uses [ViewLifecycleOwner] to provide full coverage:
 *  - [androidx.lifecycle.LifecycleOwner] - for lifecycle-aware Compose APIs
 *  - [androidx.savedstate.SavedStateRegistryOwner] - for [androidx.compose.runtime.saveable.rememberSaveable]
 *  - [androidx.lifecycle.ViewModelStoreOwner] - for [androidx.lifecycle.viewModel] composables
 *
 * Lifecycle state is driven by window visibility and focus:
 *  - CREATED  - view not visible
 *  - STARTED  - visible, no focus
 *  - RESUMED  - visible and focused
 * 
 * Usage:
 * ```
 * class SampleView(context: Context) : FrameLayout(context) {
 *     override fun onAttachedToWindow() {
 *         super.onAttachedToWindow()
 *         AxComposeInitializer.onAttachedToWindow(this)
 *     }
 *     override fun onDetachedFromWindow() {
 *         super.onDetachedFromWindow()
 *         AxComposeInitializer.onDetachedFromWindow(this)
 *     }
 * }
 * ```
 */
object AxComposeInitializer {

    fun onAttachedToWindow(root: View) {
        ViewLifecycleOwner(root).onCreate()
    }

    fun onDetachedFromWindow(root: View) {
        (root.findViewTreeLifecycleOwner() as? ViewLifecycleOwner)?.onDestroy()
    }
}
