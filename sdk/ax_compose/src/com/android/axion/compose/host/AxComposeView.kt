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
package com.android.axion.compose.host

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import com.android.axion.compose.lifecycle.AxComposeInitializer
import com.android.axion.compose.lifecycle.AxViewCompositionStrategy

/**
 * A [FrameLayout] host that safely runs Compose content in any context —
 * WindowManager overlays, services, custom Views — without requiring an Activity.
 *
 * Delegates lifecycle setup to [AxComposeInitializer]. Safe to call [setContent]
 * before or after the view is added to a window.
 * The difference with platform is, this one triggers [onAttachedToWindow] before the super method
 * seen in [frameworks/base/packages/SystemUI/src/com/android/systemui/biometrics/AuthContainerView.java]
 * while the platform docs suggests to add [AxComposeInitializer.onAttachedToWindow] after the super method
 *
 * Usage:
 * ```
 * val host = AxComposeView(context)
 * host.setContent { SampleComposable() }
 * windowManager.addView(host, layoutParams)
 * ```
 */
class AxComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val composeView = ComposeView(context).also {
        it.setViewCompositionStrategy(AxViewCompositionStrategy)
        addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        AxComposeInitializer.onAttachedToWindow(this)
        compositionContext = createLifecycleAwareWindowRecomposer()
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AxComposeInitializer.onDetachedFromWindow(this)
        compositionContext = null
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }
}
