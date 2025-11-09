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

package com.android.axion.compose.preferences

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map

enum class SettingsType { SECURE, SYSTEM, GLOBAL }

class SettingsFlow(
    private val contentResolver: ContentResolver,
    private val type: SettingsType,
) {

    fun getUri(key: String): Uri = when (type) {
        SettingsType.SECURE -> Settings.Secure.getUriFor(key)
        SettingsType.SYSTEM -> Settings.System.getUriFor(key)
        SettingsType.GLOBAL -> Settings.Global.getUriFor(key)
    }

    fun getString(key: String, default: String = ""): String = when (type) {
        SettingsType.SECURE -> Settings.Secure.getString(contentResolver, key) ?: default
        SettingsType.SYSTEM -> Settings.System.getString(contentResolver, key) ?: default
        SettingsType.GLOBAL -> Settings.Global.getString(contentResolver, key) ?: default
    }

    fun putString(key: String, value: String): Boolean = when (type) {
        SettingsType.SECURE -> Settings.Secure.putString(contentResolver, key, value)
        SettingsType.SYSTEM -> Settings.System.putString(contentResolver, key, value)
        SettingsType.GLOBAL -> Settings.Global.putString(contentResolver, key, value)
    }

    fun getInt(key: String, default: Int = 0): Int = try {
        when (type) {
            SettingsType.SECURE -> Settings.Secure.getInt(contentResolver, key, default)
            SettingsType.SYSTEM -> Settings.System.getInt(contentResolver, key, default)
            SettingsType.GLOBAL -> Settings.Global.getInt(contentResolver, key, default)
        }
    } catch (_: Exception) {
        default
    }

    fun putInt(key: String, value: Int): Boolean = when (type) {
        SettingsType.SECURE -> Settings.Secure.putInt(contentResolver, key, value)
        SettingsType.SYSTEM -> Settings.System.putInt(contentResolver, key, value)
        SettingsType.GLOBAL -> Settings.Global.putInt(contentResolver, key, value)
    }

    fun getFloat(key: String, default: Float = 0f): Float = try {
        when (type) {
            SettingsType.SECURE -> Settings.Secure.getFloat(contentResolver, key, default)
            SettingsType.SYSTEM -> Settings.System.getFloat(contentResolver, key, default)
            SettingsType.GLOBAL -> Settings.Global.getFloat(contentResolver, key, default)
        }
    } catch (_: Exception) {
        default
    }

    fun putFloat(key: String, value: Float): Boolean = when (type) {
        SettingsType.SECURE -> Settings.Secure.putFloat(contentResolver, key, value)
        SettingsType.SYSTEM -> Settings.System.putFloat(contentResolver, key, value)
        SettingsType.GLOBAL -> Settings.Global.putFloat(contentResolver, key, value)
    }

    fun observe(key: String): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(getUri(key), false, observer)
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    fun observeInt(key: String, default: Int = 0): Flow<Int> =
        observe(key).map { getInt(key, default) }

    fun observeString(key: String, default: String = ""): Flow<String> =
        observe(key).map { getString(key, default) }

    fun observeBoolean(key: String, default: Boolean = false): Flow<Boolean> =
        observeInt(key, if (default) 1 else 0).map { it != 0 }

    fun observeFloat(key: String, default: Float = 0f): Flow<Float> =
        observe(key).map { getFloat(key, default) }
}

@Composable
fun rememberSettingsFlow(type: SettingsType): SettingsFlow {
    val contentResolver = LocalContext.current.contentResolver
    return remember(type) { SettingsFlow(contentResolver, type) }
}

@Composable
fun rememberSettingInt(
    key: String,
    type: SettingsType = SettingsType.SECURE,
    default: Int = 0,
): State<Int> {
    val flow = rememberSettingsFlow(type)
    return remember(key, type) { flow.observeInt(key, default) }
        .collectAsState(initial = flow.getInt(key, default))
}

@Composable
fun rememberSettingBoolean(
    key: String,
    type: SettingsType = SettingsType.SECURE,
    default: Boolean = false,
): State<Boolean> {
    val flow = rememberSettingsFlow(type)
    return remember(key, type) { flow.observeBoolean(key, default) }
        .collectAsState(initial = flow.getInt(key, if (default) 1 else 0) != 0)
}

@Composable
fun rememberSettingString(
    key: String,
    type: SettingsType = SettingsType.SECURE,
    default: String = "",
): State<String> {
    val flow = rememberSettingsFlow(type)
    return remember(key, type) { flow.observeString(key, default) }
        .collectAsState(initial = flow.getString(key, default))
}

@Composable
fun rememberSettingFloat(
    key: String,
    type: SettingsType = SettingsType.SECURE,
    default: Float = 0f,
): State<Float> {
    val flow = rememberSettingsFlow(type)
    return remember(key, type) { flow.observeFloat(key, default) }
        .collectAsState(initial = flow.getFloat(key, default))
}
