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
import android.net.Uri
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.axion.kotlin.settings.SettingsFlow as AxSettingsFlow
import com.android.axion.kotlin.settings.SettingsType as AxSettingsType
import kotlinx.coroutines.flow.Flow

enum class SettingsType {
    SECURE,
    SYSTEM,
    GLOBAL;

    internal fun toAxSettingsType(): AxSettingsType =
        when (this) {
            SECURE -> AxSettingsType.SECURE
            SYSTEM -> AxSettingsType.SYSTEM
            GLOBAL -> AxSettingsType.GLOBAL
        }
}

class SettingsFlow @JvmOverloads constructor(
    contentResolver: ContentResolver,
    type: SettingsType,
    userId: Int = UserHandle.myUserId(),
) {
    private val delegate = AxSettingsFlow(contentResolver, type.toAxSettingsType(), userId)

    fun getUri(key: String): Uri = delegate.getUri(key)

    fun getString(key: String, default: String = ""): String = delegate.getString(key, default)

    fun putString(key: String, value: String): Boolean = delegate.putString(key, value)

    fun getInt(key: String, default: Int = 0): Int = delegate.getInt(key, default)

    fun putInt(key: String, value: Int): Boolean = delegate.putInt(key, value)

    fun getFloat(key: String, default: Float = 0f): Float = delegate.getFloat(key, default)

    fun putFloat(key: String, value: Float): Boolean = delegate.putFloat(key, value)

    @JvmOverloads
    fun observe(key: String, emitInitial: Boolean = true): Flow<Unit> =
        delegate.observe(key, emitInitial)

    fun observeInt(key: String, default: Int = 0): Flow<Int> =
        delegate.observeInt(key, default)

    fun observeString(key: String, default: String = ""): Flow<String> =
        delegate.observeString(key, default)

    fun observeBoolean(key: String, default: Boolean = false): Flow<Boolean> =
        delegate.observeBoolean(key, default)

    fun observeFloat(key: String, default: Float = 0f): Flow<Float> =
        delegate.observeFloat(key, default)
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
