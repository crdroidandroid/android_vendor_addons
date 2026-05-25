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
package com.android.axion.blur.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.axion.blur.AxBlurConfig
import com.android.axion.blur.AxBlurProperties
import com.android.axion.kotlin.settings.SettingsFlow
import com.android.axion.kotlin.settings.SettingsType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

internal class AxBackdropBlurSettingsRepository(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val appContext = context.applicationContext ?: context
    private val resolver = appContext.contentResolver
    private val globalSettings = SettingsFlow(resolver, SettingsType.GLOBAL, handler = handler)
    private val secureSettings = SettingsFlow(
        resolver,
        SettingsType.SECURE,
        currentUserId(),
        handler,
    )

    fun globalBlurEnabled(): Boolean {
        return globalSettings.getInt(
            Settings.Global.DISABLE_WINDOW_BLURS,
            if (AxBlurProperties.defaultGlobalBlurEnabled) 0 else 1,
        ) == 0
    }

    fun secureInt(key: String, default: Int): Int {
        return secureSettings.getInt(key, default)
    }

    fun secureFloat(key: String, default: Float): Float {
        return secureSettings.getFloat(key, default)
    }

    fun createSubscription(
        spec: AxBackdropBlurSettingsSpec,
        onSettingsChanged: () -> Unit,
    ): AxBackdropBlurSettingsSubscription {
        return SettingsFlowSubscription(
            flows = flows(spec),
            handler = handler,
            onSettingsChanged = onSettingsChanged,
        )
    }

    private fun flows(spec: AxBackdropBlurSettingsSpec): List<Flow<Unit>> {
        return buildList {
            add(globalSettings.observe(Settings.Global.DISABLE_WINDOW_BLURS, emitInitial = false))
            spec.enabledKey?.let { add(secureSettings.observe(it, emitInitial = false)) }
            add(secureSettings.observe(spec.radiusKey, emitInitial = false))
        }
    }

    private class SettingsFlowSubscription(
        private val flows: List<Flow<Unit>>,
        handler: Handler,
        private val onSettingsChanged: () -> Unit,
    ) : AxBackdropBlurSettingsSubscription {
        private val scope = CoroutineScope(
            SupervisorJob() + handler.asCoroutineDispatcher("AxBackdropBlurSettings"),
        )
        private var job: Job? = null

        override fun start() {
            if (job != null) return
            job = scope.launch {
                merge(*flows.toTypedArray()).collect {
                    onSettingsChanged()
                }
            }
        }

        override fun stop() {
            job?.cancel()
            job = null
        }
    }

    companion object {
        fun maxBlurRadiusPx(context: Context): Float {
            return AxBlurConfig.getMaxBlurRadiusPx(context)
        }

        private fun currentUserId(): Int {
            return UserHandle.myUserId()
        }
    }
}
